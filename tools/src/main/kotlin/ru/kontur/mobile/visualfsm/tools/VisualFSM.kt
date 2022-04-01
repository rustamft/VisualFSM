package ru.kontur.mobile.visualfsm.tools

import ru.kontur.mobile.visualfsm.Action
import ru.kontur.mobile.visualfsm.Edge
import ru.kontur.mobile.visualfsm.State
import ru.kontur.mobile.visualfsm.Transition
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.findAnnotation

object VisualFSM {

    /**
     * @return граф в формате DOT для Graphviz
     */
    fun generateDigraph(
        baseActionClass: KClass<out Action<*>>,
        baseTransitionClass: KClass<out Transition<*, *>>,
        baseState: KClass<out State>,
        initialState: KClass<out State>,
        useTransitionName: Boolean = false,
    ): String {
        val result = StringBuilder()

        result.appendLine("\ndigraph ${baseState.simpleName}Transitions {")

        result.appendLine("\"${initialState.qualifiedName!!.substringAfterLast("${baseState.simpleName}.")}\"")

        getEdgeListGraph(baseActionClass, baseTransitionClass, useTransitionName).forEach { (fromStateName, toStateName, edgeName) ->
            // Пробел перед названием action'а нужен для аккуратного отображения
            result.appendLine(
                "\"${fromStateName.simpleStateNameWithSealedName(baseState)}\" -> \"${
                    toStateName.simpleStateNameWithSealedName(baseState)
                }\" [label=\" ${edgeName}\"]"
            )
        }

        getUnreachableStates(baseActionClass, baseTransitionClass, baseState, initialState).forEach {
            result.appendLine("\"$it\" [color=\"red\"]")
        }

        result.appendLine("}\n")

        return result.toString()
    }

    /**
     * @return список ребер в виде [(начальное состояние, конечное состояние, имя ребра),...]
     */
    @Suppress("UNCHECKED_CAST")
    fun getEdgeListGraph(
        baseActionClass: KClass<out Action<*>>,
        baseTransitionClass: KClass<out Transition<*, *>>,
        useTransitionName: Boolean,
    ): List<Triple<KClass<out State>, KClass<out State>, String>> {
        val edgeList = mutableListOf<Triple<KClass<out State>, KClass<out State>, String>>()

        val actions = baseActionClass.sealedSubclasses

        actions.forEach { actionClass: KClass<out Action<*>> ->
            val transactions = actionClass.nestedClasses.filter { it.allSuperclasses.contains(baseTransitionClass) }

            transactions.forEach { transitionKClass ->
                val fromState = transitionKClass.supertypes.first().arguments.first().type?.classifier as KClass<out State>
                val toState = transitionKClass.supertypes.first().arguments.last().type?.classifier as KClass<out State>

                val nameFromEdgeAnnotation = transitionKClass.findAnnotation<Edge>()?.name

                val edgeName = when {
                    nameFromEdgeAnnotation != null -> nameFromEdgeAnnotation
                    useTransitionName -> transitionKClass.simpleName
                    else -> actionClass.simpleName
                } ?: throw IllegalStateException("Edge must have name")

                edgeList.add(
                    Triple(
                        fromState,
                        toState,
                        edgeName
                    )
                )
            }
        }

        return edgeList
    }

    /**
     * @return список недостижимых состояний от начального состояния несвязного графа, если граф связный - пустой список
     */
    fun getUnreachableStates(
        baseActionClass: KClass<out Action<*>>,
        baseTransitionClass: KClass<out Transition<*, *>>,
        baseState: KClass<out State>,
        initialState: KClass<out State>,
    ): List<KClass<out State>> {
        val result = mutableListOf<KClass<out State>>()
        val stateToVisited = mutableMapOf<KClass<out State>, Boolean>()
        val queue = LinkedList<KClass<out State>>()

        val graph = getAdjacencyMap(
            baseActionClass,
            baseTransitionClass,
            baseState,
        )

        val stateNames = graph.keys

        stateToVisited.putAll(stateNames.map { it to false })

        queue.add(initialState)
        stateToVisited[initialState] = true

        while (queue.isNotEmpty()) {
            val node = queue.poll()!!

            val iterator = graph[node]!!.iterator()
            while (iterator.hasNext()) {
                val nextNode = iterator.next()
                if (!stateToVisited[nextNode]!!) {
                    stateToVisited[nextNode] = true
                    queue.add(nextNode)
                }
            }
        }

        stateToVisited.forEach { (state, isVisited) ->
            if (!isVisited) {
                result.add(state)
            }
        }

        return result
    }

    /**
     * @return список конечных состояний
     */
    fun getFinalStates(
        baseActionClass: KClass<out Action<*>>,
        baseTransitionClass: KClass<out Transition<*, *>>,
        baseState: KClass<out State>,
    ): List<KClass<out State>> {
        val finalStates = mutableListOf<KClass<out State>>()

        val graph = getAdjacencyMap(
            baseActionClass,
            baseTransitionClass,
            baseState,
        )

        graph.forEach { (startState, destinationStates) ->
            if (destinationStates.isEmpty()) {
                finalStates.add(startState)
            }
        }

        return finalStates
    }

    /**
     * @return словарь смежности состояний в виде [(состояние to [состояние, ...]),...]
     */
    private fun getAdjacencyMap(
        baseActionClass: KClass<out Action<*>>,
        baseTransitionClass: KClass<out Transition<*, *>>,
        baseState: KClass<out State>,
    ): Map<KClass<out State>, List<KClass<out State>>> {
        val stateNames = HashSet<KClass<out State>>()
        val actions = baseActionClass.sealedSubclasses
        val graph = mutableMapOf<KClass<out State>, MutableList<KClass<out State>>>()

        populateStateNamesSet(stateNames, baseState)

        graph.putAll(stateNames.map { it to LinkedList() })

        actions.forEach { actionClass: KClass<out Action<*>> ->
            val transactions = actionClass.nestedClasses.filter { it.allSuperclasses.contains(baseTransitionClass) }

            transactions.forEach { transitionKClass ->
                val fromState = transitionKClass.supertypes.first().arguments.first().type!!.classifier as KClass<out State>
                val toState = transitionKClass.supertypes.first().arguments.last().type!!.classifier as KClass<out State>

                graph[fromState]?.add(toState)
            }
        }

        return graph
    }

    /**
     * Рекурсивное наполнение множества состояний графа,
     * каждый класс состояния может не являтся состоянием а выполнять группирующую роль
     * и иметь наследников которые являются классами состояний
     */
    private fun populateStateNamesSet(
        stateNames: HashSet<KClass<out State>>,
        stateClass: KClass<out State>,
    ) {
        stateClass.sealedSubclasses.forEach { sealedSubclass ->
            if (sealedSubclass.nestedClasses.isEmpty()) {
                stateNames.add(sealedSubclass)
            } else {
                populateStateNamesSet(stateNames, sealedSubclass)
            }
        }
    }

    private fun KClass<out State>.simpleStateNameWithSealedName(fsmName: KClass<out State>): String {
        return this.qualifiedName!!.substringAfterLast("${fsmName.simpleName}.")
    }
}
