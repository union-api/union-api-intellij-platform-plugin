package com.github.fuqiangrepository.unionapiintellijplatformplugin.model

import com.intellij.util.xmlb.annotations.Tag

@Tag("param")
class KeyValueParam {
    var key: String = ""
    var value: String = ""
    var enabled: Boolean = true
}
