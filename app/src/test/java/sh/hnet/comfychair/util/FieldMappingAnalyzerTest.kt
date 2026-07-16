package sh.hnet.comfychair.util

import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.WorkflowType
import sh.hnet.comfychair.workflow.*

class FieldMappingAnalyzerTest {

    private val registry = NodeTypeRegistry()

    @Test
    fun testCycleDetection() {
        // Construct a cycle: A -> B -> A, and B -> KSampler (positive)
        val nodeA = WorkflowNode(
            id = "A",
            classType = "CLIPTextEncode",
            title = "Encoder A",
            category = NodeCategory.ENCODER,
            inputs = mapOf("text" to InputValue.Literal("prompt A")),
            templateInputKeys = emptySet()
        )
        val nodeB = WorkflowNode(
            id = "B",
            classType = "CLIPTextEncode",
            title = "Encoder B",
            category = NodeCategory.ENCODER,
            inputs = mapOf("text" to InputValue.Literal("prompt B")),
            templateInputKeys = emptySet()
        )
        val sampler = WorkflowNode(
            id = "sampler",
            classType = "KSampler",
            title = "KSampler",
            category = NodeCategory.SAMPLER,
            inputs = mapOf("positive" to InputValue.Connection("B", 0)),
            templateInputKeys = emptySet()
        )

        // Cycles: A -> B, B -> A, B -> sampler positive input
        val edges = listOf(
            WorkflowEdge("A", 0, "B", "text"),
            WorkflowEdge("B", 0, "A", "text"),
            WorkflowEdge("B", 0, "sampler", "positive")
        )

        val graph = WorkflowGraph(
            name = "CycleTest",
            description = "",
            nodes = listOf(nodeA, nodeB, sampler),
            edges = edges,
            templateVariables = emptySet()
        )

        // Tracing from A should reach sampler positive via B and NOT loop infinitely
        val connectionType = FieldMappingAnalyzer.traceConditioningConnection(graph, "A")
        assertEquals("positive", connectionType)
    }

    @Test
    fun testPrimitiveVerification() {
        // String primitive node
        val stringPrimitive = WorkflowNode(
            id = "primitive_str",
            classType = "PrimitiveNode",
            title = "Text Primitive",
            category = NodeCategory.OTHER,
            inputs = mapOf("value" to InputValue.Literal("my prompt text")),
            templateInputKeys = emptySet()
        )

        // Int primitive node
        val intPrimitive = WorkflowNode(
            id = "primitive_int",
            classType = "PrimitiveNode",
            title = "Int Primitive",
            category = NodeCategory.OTHER,
            inputs = mapOf("value" to InputValue.Literal(123)),
            templateInputKeys = emptySet()
        )

        val graph = WorkflowGraph(
            name = "PrimitiveTest",
            description = "",
            nodes = listOf(stringPrimitive, intPrimitive),
            edges = emptyList(),
            templateVariables = emptySet()
        )

        val mappings = FieldMappingAnalyzer.createPromptFieldMappings(graph, registry)
        val positiveCandidates = mappings["positive_text"] ?: emptyList()

        // primitive_str should be a candidate, primitive_int should not
        assertTrue(positiveCandidates.any { it.nodeId == "primitive_str" })
        assertFalse(positiveCandidates.any { it.nodeId == "primitive_int" })
    }

    @Test
    fun testPrioritizationOrder() {
        // Primitive string
        val nodePrimitive = WorkflowNode(
            id = "primitive_node",
            classType = "PrimitiveNode",
            title = "Primitive Node",
            category = NodeCategory.OTHER,
            inputs = mapOf("value" to InputValue.Literal("text")),
            templateInputKeys = emptySet()
        )
        // Custom prompt node
        val nodeCustom = WorkflowNode(
            id = "custom_node",
            classType = "SmartLMLoader",
            title = "Smart LM Loader",
            category = NodeCategory.OTHER,
            inputs = mapOf("user_prompt" to InputValue.Literal("text")),
            templateInputKeys = emptySet()
        )
        // Standard CLIPTextEncode node
        val nodeClip = WorkflowNode(
            id = "clip_node",
            classType = "CLIPTextEncode",
            title = "CLIP Text Encode",
            category = NodeCategory.ENCODER,
            inputs = mapOf("text" to InputValue.Literal("text")),
            templateInputKeys = emptySet()
        )

        val graph = WorkflowGraph(
            name = "PrioritizationTest",
            description = "",
            nodes = listOf(nodePrimitive, nodeCustom, nodeClip),
            edges = emptyList(),
            templateVariables = emptySet()
        )

        val mappings = FieldMappingAnalyzer.createPromptFieldMappings(graph, registry)
        val positiveCandidates = mappings["positive_text"] ?: emptyList()

        assertEquals(3, positiveCandidates.size)
        // Priorities: CLIPTextEncode (0) -> SmartLMLoader (2) / PrimitiveNode (2) sorted alphabetically by nodeName.
        // nodeClip name = "CLIP Text Encode", nodeId = "clip_node" -> priority 0
        // nodeCustom name = "Smart LM Loader", nodeId = "custom_node" -> priority 2
        // nodePrimitive name = "Primitive Node", nodeId = "primitive_node" -> priority 2
        // Alphabetical sort for priority 2: "Primitive Node" then "Smart LM Loader".
        // Let's assert:
        assertEquals("clip_node", positiveCandidates[0].nodeId)
        assertEquals("primitive_node", positiveCandidates[1].nodeId)
        assertEquals("custom_node", positiveCandidates[2].nodeId)
    }

    @Test
    fun testBlacklistFiltering() {
        val node = WorkflowNode(
            id = "test_node",
            classType = "CustomNode",
            title = "Custom Node",
            category = NodeCategory.OTHER,
            inputs = mapOf(
                "user_prompt" to InputValue.Literal("my prompt"),
                "texture_path" to InputValue.Literal("path/to/texture.png"),
                "template_file" to InputValue.Literal("template.json"),
                "text_url" to InputValue.Literal("http://example.com"),
                "positive_text" to InputValue.Literal("positive text")
            ),
            templateInputKeys = emptySet()
        )

        val graph = WorkflowGraph(
            name = "BlacklistTest",
            description = "",
            nodes = listOf(node),
            edges = emptyList(),
            templateVariables = emptySet()
        )

        val mappings = FieldMappingAnalyzer.createPromptFieldMappings(graph, registry)
        val positiveCandidates = mappings["positive_text"] ?: emptyList()

        // user_prompt and positive_text should be matched, other inputs should not
        assertTrue(positiveCandidates.any { it.inputKey == "user_prompt" })
        assertTrue(positiveCandidates.any { it.inputKey == "positive_text" })
        assertFalse(positiveCandidates.any { it.inputKey == "texture_path" })
        assertFalse(positiveCandidates.any { it.inputKey == "template_file" })
        assertFalse(positiveCandidates.any { it.inputKey == "text_url" })
    }

    @Test
    fun testDeepChainTracing() {
        // PrimitiveNode -> StringConcatenate -> CLIPTextEncode -> KSampler
        val primitive = WorkflowNode(
            id = "primitive_id",
            classType = "PrimitiveNode",
            title = "Text Primitive",
            category = NodeCategory.OTHER,
            inputs = mapOf("value" to InputValue.Literal("Hello")),
            templateInputKeys = emptySet()
        )
        val concat = WorkflowNode(
            id = "concat_id",
            classType = "StringConcatenate",
            title = "String Concatenate",
            category = NodeCategory.OTHER,
            inputs = mapOf("text_a" to InputValue.Connection("primitive_id", 0)),
            templateInputKeys = emptySet()
        )
        val clip = WorkflowNode(
            id = "clip_id",
            classType = "CLIPTextEncode",
            title = "CLIP Text Encode",
            category = NodeCategory.ENCODER,
            inputs = mapOf("text" to InputValue.Connection("concat_id", 0)),
            templateInputKeys = emptySet()
        )
        val sampler = WorkflowNode(
            id = "sampler_id",
            classType = "KSampler",
            title = "KSampler",
            category = NodeCategory.SAMPLER,
            inputs = mapOf("positive" to InputValue.Connection("clip_id", 0)),
            templateInputKeys = emptySet()
        )

        val edges = listOf(
            WorkflowEdge("primitive_id", 0, "concat_id", "text_a"),
            WorkflowEdge("concat_id", 0, "clip_id", "text"),
            WorkflowEdge("clip_id", 0, "sampler_id", "positive")
        )

        val graph = WorkflowGraph(
            name = "DeepChainTest",
            description = "",
            nodes = listOf(primitive, concat, clip, sampler),
            edges = edges,
            templateVariables = emptySet()
        )

        val connectionType = FieldMappingAnalyzer.traceConditioningConnection(graph, "primitive_id")
        assertEquals("positive", connectionType)
    }

    @Test
    fun testUnetWorkflowDetection() {
        val unetJson = """
            {
              "nodes": {
                "1": {
                  "class_type": "UnetLoaderGGUF",
                  "inputs": {
                    "unet_name": "flux1-dev-Q4_K_S.gguf"
                  }
                }
              }
            }
        """.trimIndent()
        val detected = WorkflowJsonAnalyzer.detectWorkflowType(unetJson)
        assertEquals(WorkflowType.TTI, detected)
    }

    @Test
    fun testSmartFieldMappingForLoaders() {
        // Test custom UNET loader
        val isUnetMatch = TemplateKeyRegistry.isFieldMatch(
            fieldKey = "unet_name",
            classType = "UnetLoaderGGUF",
            inputKey = "unet_name",
            inputValue = "flux1-dev-Q4_K_S.gguf"
        )
        assertTrue(isUnetMatch)

        // Custom UNET loader should NOT match checkpoint
        val isCkptMatch = TemplateKeyRegistry.isFieldMatch(
            fieldKey = "ckpt_name",
            classType = "UnetLoaderGGUF",
            inputKey = "unet_name",
            inputValue = "flux1-dev-Q4_K_S.gguf"
        )
        assertFalse(isCkptMatch)

        // Custom checkpoint loader with "model_name" should match checkpoint
        val isCustomCkptMatch = TemplateKeyRegistry.isFieldMatch(
            fieldKey = "ckpt_name",
            classType = "CheckpointLoaderGGUF",
            inputKey = "model_name",
            inputValue = "sd15.safetensors"
        )
        assertTrue(isCustomCkptMatch)

        // Custom checkpoint loader with "model_name" should NOT match UNET
        val isCustomCkptUnetMatch = TemplateKeyRegistry.isFieldMatch(
            fieldKey = "unet_name",
            classType = "CheckpointLoaderGGUF",
            inputKey = "model_name",
            inputValue = "sd15.safetensors"
        )
        assertFalse(isCustomCkptUnetMatch)
    }

    @Test
    fun testPromptKeyAndTitleFallbackClassification() {
        val positiveNode = WorkflowNode(
            id = "node_pos",
            classType = "CustomPromptEncoder",
            title = "My {posprompt} Node",
            category = NodeCategory.OTHER,
            inputs = mapOf("posprompt" to InputValue.Literal("positive prompt text")),
            templateInputKeys = emptySet()
        )
        val negativeNode = WorkflowNode(
            id = "node_neg",
            classType = "CustomPromptEncoder",
            title = "My {negprompt} Node",
            category = NodeCategory.OTHER,
            inputs = mapOf("negprompt" to InputValue.Literal("negative prompt text")),
            templateInputKeys = emptySet()
        )

        val graph = WorkflowGraph(
            name = "FallbackClassificationTest",
            description = "",
            nodes = listOf(positiveNode, negativeNode),
            edges = emptyList(),
            templateVariables = emptySet()
        )

        val mappings = FieldMappingAnalyzer.createPromptFieldMappings(graph, registry)
        val positiveCandidates = mappings["positive_text"] ?: emptyList()
        val negativeCandidates = mappings["negative_text"] ?: emptyList()

        // positiveNode should only be in positiveCandidates, not negativeCandidates
        assertTrue(positiveCandidates.any { it.nodeId == "node_pos" })
        assertFalse(negativeCandidates.any { it.nodeId == "node_pos" })

        // negativeNode should only be in negativeCandidates, not positiveCandidates
        assertTrue(negativeCandidates.any { it.nodeId == "node_neg" })
        assertFalse(positiveCandidates.any { it.nodeId == "node_neg" })
    }

    @Test
    fun testModelLoaderFalsePositivePrevention() {
        // A preprocessor class type should NOT match "ckpt_name"
        val isMatch = TemplateKeyRegistry.isFieldMatch(
            fieldKey = "ckpt_name",
            classType = "MyCheckpointPreprocessor",
            inputKey = "model_name",
            inputValue = "some_value"
        )
        assertFalse(isMatch)

        // A loader class type should still match
        val isLoaderMatch = TemplateKeyRegistry.isFieldMatch(
            fieldKey = "ckpt_name",
            classType = "CheckpointLoaderSimple",
            inputKey = "model_name",
            inputValue = "some_value"
        )
        assertTrue(isLoaderMatch)
    }
}
