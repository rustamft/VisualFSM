package ru.kontur.mobile.visualfsm

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Manages the start and stop of state-based asynchronous tasks
 */
abstract class AsyncWorker<STATE : State, ACTION : Action<STATE>> {

    /**
     * [The coroutine scope][CoroutineScope] for the currently running async task
     */
    protected open val taskScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * [The coroutine scope][CoroutineScope] used to subscribe
     * to [feature's][Feature] [flow of states][State]
     */
    protected open val subscriptionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var feature: Feature<STATE, ACTION>? = null
    private var launchedAsyncState: STATE? = null
    private var subscriptionJob: Job? = null
    private var launchedAsyncStateJob: Job? = null

    /**
     * Binds received [feature][Feature] to [async worker][AsyncWorker]
     * and starts [observing][Feature.observeState] [states][State]
     *
     * @param feature provided [Feature]
     */
    internal fun bind(feature: Feature<STATE, ACTION>) {
        this.feature = feature
        subscriptionJob = subscriptionScope.launch {
            feature.observeState().map {
                onNextState(it)
            }.onEach {
                handleTask(it)
            }.catch {
                onStateSubscriptionError(it)
            }.collect()
        }
    }

    /**
     * Cancel current task and unbind feature. Use it if the async worker is no longer needed (onCleared)
     * If you only need to stop the current task, use feature.proceed(_SomeActionForStop_())
     */
    fun unbind() {
        cancel()
        subscriptionJob?.cancel()
        feature = null
    }

    /**
     * Provides a state to manage async work
     * Don't forget to handle each task's errors in this method,
     * if an unhandled exception occurs, then fsm may stuck in the current state
     * and the onStateSubscriptionError method will be called
     *
     * @param state a next [state][State]
     * @return [AsyncWorkerTask] for async work handling
     */
    protected abstract fun onNextState(state: STATE): AsyncWorkerTask<STATE>

    /**
     * Called when catched subscription error
     * Override this for logs or metrics
     * Call of this method signals the presence of unhandled exceptions in the [onNextState] method.
     * @param throwable catched [Throwable]
     */
    protected open fun onStateSubscriptionError(throwable: Throwable) {
        throw throwable
    }

    /**
     * Submits an [action][Action] to be executed to the [feature][Feature]
     *
     * @param action [Action] to run
     */
    fun proceed(action: ACTION) {
        feature?.proceed(action) ?: throw IllegalStateException("Feature is unbound")
    }

    /**
     * Handle new task
     */
    private fun handleTask(task: AsyncWorkerTask<STATE>) {
        when (task) {
            is AsyncWorkerTask.Cancel -> cancel()
            is AsyncWorkerTask.ExecuteAndCancelExist -> {
                cancelAndLaunch(task.state, task.func)
            }
            is AsyncWorkerTask.ExecuteIfNotExist -> {
                if (launchedAsyncStateJob?.isActive != true || task.state != launchedAsyncState) {
                    cancelAndLaunch(task.state, task.func)
                }
            }
        }
    }

    /**
     * Cancel current task and launch new
     */
    private fun cancelAndLaunch(stateToLaunch: STATE, func: suspend () -> Unit) {
        launchedAsyncState = stateToLaunch
        launchedAsyncStateJob?.cancel()
        launchedAsyncStateJob = taskScope.launch { func() }
    }

    /**
     * Cancel current task
     */
    private fun cancel() {
        launchedAsyncStateJob?.cancel()
        launchedAsyncState = null
    }
}