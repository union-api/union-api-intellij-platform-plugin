package com.github.fuqiangrepository.unionapiintellijplatformplugin.services

import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.ApiState
import com.intellij.openapi.project.Project
import org.mozilla.javascript.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest as JavaHttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object ScriptExecutor {

    fun runPreScript(script: String, project: Project) {
        if (script.isBlank()) return
        val state = ApiStateService.getInstance(project).state
        runScript(script, state, preRequestContext = true, response = null)
    }

    fun runPostScript(script: String, response: HttpResponse<String>, project: Project) {
        if (script.isBlank()) return
        val state = ApiStateService.getInstance(project).state
        runScript(script, state, preRequestContext = false, response = response)
    }

    private fun runScript(
        script: String,
        state: ApiState,
        preRequestContext: Boolean,
        response: HttpResponse<String>?
    ) {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            val scope = cx.initStandardObjects()

            val pm = cx.newObject(scope)

            // pm.environment
            val env = cx.newObject(scope)
            ScriptableObject.putProperty(env, "get", object : BaseFunction() {
                override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>): Any? {
                    val key = args.getOrNull(0)?.toString() ?: return null
                    return state.environment[key]
                }
            })
            ScriptableObject.putProperty(env, "set", object : BaseFunction() {
                override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>): Any? {
                    val key = args.getOrNull(0)?.toString() ?: return null
                    val value = args.getOrNull(1)?.toString() ?: ""
                    state.environment[key] = value
                    return null
                }
            })
            ScriptableObject.putProperty(pm, "environment", env)

            // pm.sendRequest (pre-request only)
            if (preRequestContext) {
                ScriptableObject.putProperty(pm, "sendRequest", object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>): Any? {
                        val config = args.getOrNull(0) as? Scriptable ?: return null
                        return executeSendRequest(cx, scope, config)
                    }
                })
            }

            // pm.response (post-response only)
            if (!preRequestContext && response != null) {
                val resp = cx.newObject(scope)
                ScriptableObject.putProperty(resp, "status", response.statusCode())
                ScriptableObject.putProperty(resp, "body", response.body())
                ScriptableObject.putProperty(resp, "headers", buildHeadersObject(cx, scope, response))
                ScriptableObject.putProperty(resp, "json", object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>): Any? {
                        return try {
                            cx.evaluateString(scope, "(${response.body()})", "json", 1, null)
                        } catch (_: Exception) { null }
                    }
                })
                ScriptableObject.putProperty(pm, "response", resp)
            }

            ScriptableObject.putProperty(scope, "pm", pm)
            cx.evaluateString(scope, script, "script", 1, null)
        } catch (e: Exception) {
            // Script errors are non-fatal; could log them here
        } finally {
            Context.exit()
        }
    }

    private fun executeSendRequest(cx: Context, scope: Scriptable, config: Scriptable): Scriptable {
        val method = ScriptableObject.getProperty(config, "method")?.toString() ?: "GET"
        val url = ScriptableObject.getProperty(config, "url")?.toString() ?: ""
        val body = ScriptableObject.getProperty(config, "body")?.toString()
        val headersObj = ScriptableObject.getProperty(config, "headers") as? Scriptable

        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        val builder = JavaHttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))

        headersObj?.ids?.forEach { key ->
            val k = key.toString()
            val v = ScriptableObject.getProperty(headersObj, k)?.toString() ?: ""
            builder.header(k, v)
        }

        val bodyPublisher = if (body != null && method != "GET")
            JavaHttpRequest.BodyPublishers.ofString(body)
        else
            JavaHttpRequest.BodyPublishers.noBody()

        val httpReq = when (method.uppercase()) {
            "GET"    -> builder.GET().build()
            "DELETE" -> builder.DELETE().build()
            else     -> builder.method(method.uppercase(), bodyPublisher).build()
        }

        val response = client.send(httpReq, HttpResponse.BodyHandlers.ofString())

        val result = cx.newObject(scope)
        ScriptableObject.putProperty(result, "status", response.statusCode())
        ScriptableObject.putProperty(result, "body", response.body())
        ScriptableObject.putProperty(result, "json", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>): Any? {
                return try {
                    cx.evaluateString(scope, "(${response.body()})", "json", 1, null)
                } catch (_: Exception) { null }
            }
        })
        return result
    }

    private fun buildHeadersObject(cx: Context, scope: Scriptable, response: HttpResponse<String>): Scriptable {
        val headers = cx.newObject(scope)
        response.headers().map().forEach { (k, v) ->
            ScriptableObject.putProperty(headers, k, v.joinToString(", "))
        }
        return headers
    }
}
