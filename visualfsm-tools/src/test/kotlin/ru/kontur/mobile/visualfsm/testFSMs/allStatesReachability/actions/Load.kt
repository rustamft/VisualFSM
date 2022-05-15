package ru.kontur.mobile.visualfsm.testFSMs.allStatesReachability.actions

import ru.kontur.mobile.visualfsm.Transition
import ru.kontur.mobile.visualfsm.testFSMs.allStatesReachability.AllStatesReachabilityFSMState.AsyncWorkState.Loading
import ru.kontur.mobile.visualfsm.testFSMs.allStatesReachability.AllStatesReachabilityFSMState.AsyncWorkState.Updating
import ru.kontur.mobile.visualfsm.testFSMs.allStatesReachability.AllStatesReachabilityFSMState.Initial
import ru.kontur.mobile.visualfsm.testFSMs.allStatesReachability.AllStatesReachabilityFSMState.Loaded

class Load : AllStatesReachabilityFSMAction() {

    inner class InitialToLoadingTransition : Transition<Initial, Loading>(
        Initial::class,
        Loading::class
    ) {

        override fun transform(state: Initial) = Loading
    }

    inner class LoadingToLoadingTransition : Transition<Loading, Loading>(
        Loading::class,
        Loading::class
    ) {

        override fun transform(state: Loading) = state
    }

    inner class LoadedToUpdatingTransition : Transition<Loaded, Updating>(
        Loaded::class,
        Updating::class
    ) {

        override fun transform(state: Loaded) = Updating
    }

    inner class UpdatingToUpdatingTransition : Transition<Updating, Updating>(
        Updating::class,
        Updating::class
    ) {

        override fun transform(state: Updating) = state
    }

    override fun getTransitions() = listOf(
        InitialToLoadingTransition(),
        LoadingToLoadingTransition(),
        LoadedToUpdatingTransition(),
        UpdatingToUpdatingTransition()
    )
}
