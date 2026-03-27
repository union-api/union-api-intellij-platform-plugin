package com.github.fuqiangrepository.unionapiintellijplatformplugin.services

import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.ApiState
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "UnionApiState",
    storages = [Storage("union-api.xml")]
)
class ApiStateService : PersistentStateComponent<ApiState> {

    private var state = ApiState()

    override fun getState(): ApiState = state

    override fun loadState(state: ApiState) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): ApiStateService = project.service()
    }
}
