package com.github.fuqiangrepository.unionapiintellijplatformplugin.model

import com.intellij.util.xmlb.annotations.Tag
import java.util.UUID

@Tag("request")
class ApiRequest {
    var id: String = UUID.randomUUID().toString()
    var name: String = "New Request"
    var method: String = "GET"
    var url: String = ""
    var headers: ArrayList<KeyValueParam> = ArrayList()
    var params: ArrayList<KeyValueParam> = ArrayList()
    var bodyType: String = "none"
    var body: String = ""
}
