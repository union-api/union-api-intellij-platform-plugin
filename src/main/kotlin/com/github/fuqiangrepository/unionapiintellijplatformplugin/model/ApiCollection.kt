package com.github.fuqiangrepository.unionapiintellijplatformplugin.model

import com.intellij.util.xmlb.annotations.Tag
import java.util.UUID

@Tag("collection")
class ApiCollection {
    var id: String = UUID.randomUUID().toString()
    var name: String = "New Collection"
    var requests: ArrayList<ApiRequest> = ArrayList()
}
