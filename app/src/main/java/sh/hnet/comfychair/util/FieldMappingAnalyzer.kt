package sh.hnet.comfychair.util

import android.content.Context
import sh.hnet.comfychair.WorkflowType
import sh.hnet.comfychair.workflow.FieldCandidate
import sh.hnet.comfychair.workflow.FieldDisplayRegistry
import sh.hnet.comfychair.workflow.FieldMappingState
import sh.hnet.comfychair.workflow.InputValue
import sh.hnet.comfychair.workflow.NodeTypeRegistry
import sh.hnet.comfychair.workflow.TemplateKeyRegistry
import sh.hnet.comfychair.workflow.WorkflowGraph
import sh.hnet.comfychair.workflow.WorkflowMappingState
import sh.hnet.comfychair.workflow.WorkflowNode

/**
 * Analyzes workflow graphs to detect field mapping candidates.
 *
 * Provides pure functions for creating field mappings, finding candidates,
 * and tracing connections in workflow graphs.
 */
object FieldMappingAnalyzer {

    /**
     * Create field mapping state by analyzing graph nodes.
     *
     * @param context Context for string resources
     * @param graph The workflow graph to analyze
     * @param type The workflow type
     * @param nodeTypeRegistry Registry for looking up node definitions
     * @return Complete mapping state for the workflow
     */
    fun createFieldMappingState(
        context: Context,
        graph: WorkflowGraph,
        type: WorkflowType,
        nodeTypeRegistry: NodeTypeRegistry
    ): WorkflowMappingState {
        // Get strictly required keys for this workflow type
        val strictlyRequiredKeys = TemplateKeyRegistry.getRequiredKeysForType(type)

        // Get optional keys, adjusted for workflow structure (DualCLIPLoader, BasicGuider)
        val optionalKeys = getOptionalKeysFromGraph(type, graph)

        // Pre-compute prompt field mappings using graph tracing
        val promptMappings = createPromptFieldMappings(graph, nodeTypeRegistry)

        val fieldMappings = mutableListOf<FieldMappingState>()

        // Process strictly required keys (isRequired = true)
        for (fieldKey in strictlyRequiredKeys) {
            val candidates = findCandidatesForField(fieldKey, graph, promptMappings, nodeTypeRegistry)
            val requiredField = FieldDisplayRegistry.createRequiredField(context, fieldKey, isRequired = true)

            fieldMappings.add(
                FieldMappingState(
                    field = requiredField,
                    candidates = candidates,
                    selectedCandidateIndex = autoSelectIndex(fieldKey, candidates)
                )
            )
        }

        // Process optional keys (isRequired = false)
        for (fieldKey in optionalKeys) {
            val candidates = findCandidatesForField(fieldKey, graph, promptMappings, nodeTypeRegistry)
            val requiredField = FieldDisplayRegistry.createRequiredField(context, fieldKey, isRequired = false)

            fieldMappings.add(
                FieldMappingState(
                    field = requiredField,
                    candidates = candidates,
                    selectedCandidateIndex = autoSelectIndex(fieldKey, candidates)
                )
            )
        }

        return WorkflowMappingState(
            workflowType = type,
            fieldMappings = fieldMappings
        )
    }

    /**
     * Get optional keys for a workflow type, adjusted for graph structure.
     * Handles BasicGuider (no CFG/negative).
     *
     * @param type The workflow type
     * @param graph The workflow graph
     * @return Set of optional field keys
     */
    fun getOptionalKeysFromGraph(type: WorkflowType, graph: WorkflowGraph): Set<String> {
        val baseKeys = TemplateKeyRegistry.getOptionalKeysForType(type).toMutableSet()

        if (type == WorkflowType.TTI) {
            // Check for BasicGuider (no CFG, no negative prompt)
            val hasBasicGuider = graph.nodes.any { it.classType == "BasicGuider" }
            if (hasBasicGuider) {
                baseKeys.remove("cfg")
                baseKeys.remove("negative_text")
            }
        }

        return baseKeys
    }

    /**
     * Find candidates for a field in the graph.
     * Uses two detection strategies:
     * 1. Input-key-based: Look for nodes with matching input key names (fast path)
     * 2. Output-type-based: For "image" field, look for nodes with IMAGE output + ENUM inputs (fallback)
     *
     * @param fieldKey The field key to find candidates for
     * @param graph The workflow graph
     * @param promptMappings Pre-computed prompt field mappings
     * @param nodeTypeRegistry Registry for looking up node definitions
     * @return List of candidates matching the field
     */
    fun findCandidatesForField(
        fieldKey: String,
        graph: WorkflowGraph,
        promptMappings: Map<String, List<FieldCandidate>>,
        nodeTypeRegistry: NodeTypeRegistry
    ): List<FieldCandidate> {
        // Handle positive_text and negative_text with graph tracing
        if (fieldKey == "positive_text" || fieldKey == "negative_text") {
            return promptMappings[fieldKey] ?: emptyList()
        }

        // Strategy 1: Find nodes that have matching input key names
        val directCandidates = mutableListOf<FieldCandidate>()

        for (node in graph.nodes) {
            node.inputs.forEach { (inputName, inputValue) ->
                if (TemplateKeyRegistry.doesInputKeyMatchField(fieldKey, inputName)) {
                    val currentValue = when (inputValue) {
                        is InputValue.Literal -> inputValue.value
                        else -> null
                    }

                    if (TemplateKeyRegistry.doesValueMatchPlaceholder(fieldKey, currentValue)) {
                        directCandidates.add(
                            FieldCandidate(
                                nodeId = node.id,
                                nodeName = node.title,
                                classType = node.classType,
                                inputKey = inputName,
                                currentValue = currentValue
                            )
                        )
                    }
                }
            }
        }

        // Strategy 2 (fallback): For "image" field, find nodes with IMAGE output + ENUM inputs
        // This works regardless of input key language (Chinese, etc.)
        if (directCandidates.isEmpty() && fieldKey == "image") {
            for (node in graph.nodes) {
                if (node.outputs.any { it.type == "IMAGE" }) {
                    val definition = nodeTypeRegistry.getNodeDefinition(node.classType)
                    definition?.inputs
                        ?.filter { it.type == "ENUM" && node.inputs.containsKey(it.name) }
                        ?.forEach { inputDef ->
                            val inputValue = node.inputs[inputDef.name]
                            val currentValue = when (inputValue) {
                                is InputValue.Literal -> inputValue.value
                                else -> null
                            }
                            directCandidates.add(
                                FieldCandidate(
                                    nodeId = node.id,
                                    nodeName = node.title,
                                    classType = node.classType,
                                    inputKey = inputDef.name,
                                    currentValue = currentValue
                                )
                            )
                        }
                }
            }
        }

        return directCandidates
    }

    /**
     * Helper to verify if an input key is a candidate for a text prompt.
     */
    private fun isPromptInputKey(key: String, classType: String): Boolean {
        val keyLower = key.lowercase()
        if (keyLower == "value" && classType == "PrimitiveNode") return true

        val isMatchedName = keyLower == "text" || keyLower == "prompt" ||
                keyLower.endsWith("text") || keyLower.endsWith("prompt") ||
                keyLower.contains("posprompt") || keyLower.contains("negprompt") ||
                keyLower.contains("pos_prompt") || keyLower.contains("neg_prompt") ||
                keyLower.contains("{posprompt}") || keyLower.contains("{negprompt}")

        val hasObviousNonPromptTerm = keyLower.contains("file") ||
                keyLower.contains("path") ||
                keyLower.contains("template") ||
                keyLower.contains("url") ||
                keyLower.contains("dir")

        return isMatchedName && !hasObviousNonPromptTerm
    }

    /**
     * Create field mappings for positive_text and negative_text using graph tracing.
     * Uses two detection strategies:
     * 1. Input-key-based: Look for nodes with text/prompt input keys or string primitives
     * 2. Output-type-based: Look for nodes with CONDITIONING output + STRING inputs (fallback for custom nodes)
     *
     * @param graph The workflow graph
     * @param nodeTypeRegistry Registry for looking up node definitions
     * @return Map of field keys to their candidates
     */
    fun createPromptFieldMappings(
        graph: WorkflowGraph,
        nodeTypeRegistry: NodeTypeRegistry
    ): Map<String, List<FieldCandidate>> {
        val positiveTextCandidates = mutableListOf<FieldCandidate>()
        val negativeTextCandidates = mutableListOf<FieldCandidate>()

        // Strategy 1: Find nodes with text/prompt input keys or string primitives
        data class TextEncoderNode(val node: WorkflowNode, val inputKey: String)
        val textEncoderNodes = graph.nodes.flatMap { node ->
            node.inputs.filter { (key, value) ->
                val isStringLiteral = value is InputValue.Literal && value.value is String
                isPromptInputKey(key, node.classType) && isStringLiteral
            }.map { (key, _) ->
                TextEncoderNode(node, key)
            }
        }

        // Strategy 2 (fallback): Find nodes with CONDITIONING output + STRING inputs
        // This works regardless of input key language (Chinese, etc.)
        val outputBasedNodes = if (textEncoderNodes.isEmpty()) {
            graph.nodes.flatMap { node ->
                if (node.outputs.any { it.type == "CONDITIONING" }) {
                    val definition = nodeTypeRegistry.getNodeDefinition(node.classType)
                    definition?.inputs
                        ?.filter { it.type == "STRING" && node.inputs.containsKey(it.name) }
                        ?.map { inputDef -> TextEncoderNode(node, inputDef.name) }
                        ?: emptyList()
                } else {
                    emptyList()
                }
            }
        } else {
            emptyList()
        }

        // Combine both strategies
        val allTextEncoderNodes = textEncoderNodes + outputBasedNodes

        // For each text encoder, trace its output to find if it connects to positive or negative
        for ((node, inputKey) in allTextEncoderNodes) {
            val textInput = node.inputs[inputKey]
            val currentValue = when (textInput) {
                is InputValue.Literal -> textInput.value
                else -> null
            }

            val candidate = FieldCandidate(
                nodeId = node.id,
                nodeName = node.title,
                classType = node.classType,
                inputKey = inputKey,
                currentValue = currentValue
            )

            // Determine if this node connects to positive or negative input
            val connectionType = traceConditioningConnection(graph, node.id)

            when (connectionType) {
                "positive" -> positiveTextCandidates.add(0, candidate) // Add traced match first
                "negative" -> negativeTextCandidates.add(0, candidate) // Add traced match first
                else -> {
                    // Unknown connection - use title-based/key-based classification
                    val titleLower = node.title.lowercase()
                    val keyLower = inputKey.lowercase()
                    
                    val isPositive = titleLower.contains("positive") || 
                                     titleLower.contains("posprompt") || 
                                     titleLower.contains("pos_prompt") || 
                                     titleLower.contains("{posprompt}") || 
                                     titleLower.contains("pos") ||
                                     keyLower.contains("positive") || 
                                     keyLower.contains("posprompt") || 
                                     keyLower.contains("pos_prompt") || 
                                     keyLower.contains("pos")

                    val isNegative = titleLower.contains("negative") || 
                                     titleLower.contains("negprompt") || 
                                     titleLower.contains("neg_prompt") || 
                                     titleLower.contains("{negprompt}") || 
                                     titleLower.contains("neg") ||
                                     keyLower.contains("negative") || 
                                     keyLower.contains("negprompt") || 
                                     keyLower.contains("neg_prompt") || 
                                     keyLower.contains("neg")

                    when {
                        isPositive && !isNegative -> positiveTextCandidates.add(candidate)
                        isNegative && !isPositive -> negativeTextCandidates.add(candidate)
                        else -> {
                            // Add to both as fallback candidates
                            positiveTextCandidates.add(candidate)
                            negativeTextCandidates.add(candidate)
                        }
                    }
                }
            }
        }

        // Prioritize CLIPTextEncode and other standard encoders (stable sort)
        val priorityComparator = compareBy<FieldCandidate> { candidate ->
            when {
                candidate.classType == "CLIPTextEncode" -> 0
                candidate.classType.contains("TextEncode", ignoreCase = true) ||
                candidate.classType.contains("Prompt", ignoreCase = true) -> 1
                else -> 2
            }
        }.thenBy { it.nodeName }.thenBy { it.nodeId }

        val sortedPositive = positiveTextCandidates.sortedWith(priorityComparator)
        val sortedNegative = negativeTextCandidates.sortedWith(priorityComparator)

        return mapOf(
            "positive_text" to sortedPositive,
            "negative_text" to sortedNegative
        )
    }

    /**
     * Choose the auto-selected candidate index for a field.
     *
     * Prefers the candidate whose current value already matches the expected placeholder
     * (set by a previous save), so re-editing a workflow keeps the correct node selected
     * rather than defaulting to whichever node happens to be first in iteration order.
     * Falls back to index 0 if no placeholder match is found.
     */
    private fun autoSelectIndex(fieldKey: String, candidates: List<FieldCandidate>): Int {
        if (candidates.isEmpty()) return -1
        val placeholderPattern = "{{${TemplateKeyRegistry.getPlaceholderForKey(fieldKey)}}}"
        val preferred = candidates.indexOfFirst { it.currentValue?.toString() == placeholderPattern }
        return if (preferred >= 0) preferred else 0
    }

    /**
     * Trace a conditioning node's output to find what type of connection it has.
     * Uses WorkflowGraph.edges to trace connections recursively with cycle detection.
     *
     * This is classType-agnostic: it simply checks if the target input name
     * indicates positive or negative conditioning, regardless of the target node type.
     *
     * @param graph The workflow graph
     * @param currentNodeId ID of the node to trace from
     * @param visited Set of already visited nodes to prevent cycles
     * @return "positive", "negative", or null if no connection found
     */
    fun traceConditioningConnection(
        graph: WorkflowGraph,
        currentNodeId: String,
        visited: Set<String> = emptySet()
    ): String? {
        if (currentNodeId in visited) {
            // Cycle detected
            return null
        }
        if (visited.size > 15) {
            // Max depth reached
            DebugLogger.w("FieldMappingAnalyzer", "Max depth (15) exceeded tracing node: $currentNodeId")
            return null
        }
        val nextVisited = visited + currentNodeId

        // Find edges originating from this conditioning node
        val outgoingEdges = graph.edges.filter { it.sourceNodeId == currentNodeId }

        // Step 1: Check direct connections
        for (edge in outgoingEdges) {
            // Check target input name - works for any node type (KSampler, custom samplers, etc.)
            when (edge.targetInputName.lowercase()) {
                "positive" -> return "positive"
                "negative" -> return "negative"
                "conditioning" -> return "positive"  // Single conditioning input (like BasicGuider)
            }
        }

        // Step 2: Recurse downstream
        for (edge in outgoingEdges) {
            val result = traceConditioningConnection(graph, edge.targetNodeId, nextVisited)
            if (result != null) {
                return result
            }
        }

        return null
    }
}
