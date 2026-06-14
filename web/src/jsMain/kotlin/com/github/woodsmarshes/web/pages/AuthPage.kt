package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.network.api.rest.AuthApi
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.koinInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.useState
import web.cssom.ClassName
import web.html.InputType
import web.html.email
import web.html.password

private fun parseQueryParams(): Map<String, String> {
    val hash = kotlinx.browser.window.location.hash
    val queryStart = hash.indexOf('?')
    if (queryStart < 0) return emptyMap()
    val query = hash.substring(queryStart + 1)
    return query.split('&').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq < 0) null
        else part.substring(0, eq) to part.substring(eq + 1)
    }.toMap()
}

val AuthPage = FC<Props> {
    val params = parseQueryParams()
    val returnUrl = params["returnUrl"] ?: "/"

    var isLogin by useState(true)
    var email by useState("")
    var password by useState("")
    var username by useState("")
    var errorMsg by useState<String?>(null)
    var submitting by useState(false)

    val handleSubmit = {
        if (!submitting) {
            errorMsg = null
            submitting = true

            koinInject<CoroutineScope>().promise {
            try {
                val authApi = koinInject<AuthApi>()
                val tokenDs = koinInject<AuthTokenDataSource>()
                    if (isLogin) {
                        val resp = authApi.login(email, password)
                        tokenDs.setJwtToken(resp.accessToken)
                    } else {
                        val resp = authApi.register(username, email, password)
                        tokenDs.setJwtToken(resp.accessToken)
                    }
                Router.navigate(returnUrl)
            } catch (e: Exception) {
                    errorMsg = e.message ?: "Authentication failed"
                    submitting = false
                }
            }
        }
    }

    div {
        className = ClassName("auth-page")

        div {
            className = ClassName("auth-card")

            h1 {
                className = ClassName("auth-title")
                +if (isLogin) "Login" else "Register"
            }

            if (!isLogin) {
                div {
                    className = ClassName("auth-field")
                    input {
                        className = ClassName("auth-input")
                        placeholder = "Username"
                        value = username
                        onChange = { event -> username = event.target.value.unsafeCast<String>() }
                    }
                }
            }

            div {
                className = ClassName("auth-field")
                input {
                    className = ClassName("auth-input")
                    type = InputType.email
                    placeholder = "Email"
                    value = email
                    onChange = { event -> email = event.target.value.unsafeCast<String>() }
                }
            }

            div {
                className = ClassName("auth-field")
                input {
                    className = ClassName("auth-input")
                    type = InputType.password
                    placeholder = "Password"
                    value = password
                    onChange = { event -> password = event.target.value.unsafeCast<String>() }
                }
            }

            if (errorMsg != null) {
                p {
                    className = ClassName("auth-error")
                    +(errorMsg ?: "")
                }
            }

            button {
                className = ClassName("btn btn-primary auth-submit")
                disabled = submitting
                onClick = { handleSubmit() }
                +if (submitting) "..." else if (isLogin) "Login" else "Register"
            }

            p {
                className = ClassName("auth-toggle")
                +if (isLogin) "Don't have an account? " else "Already have an account? "
                a {
                    href = "#"
                    onClick = { event ->
                        event.preventDefault()
                        isLogin = !isLogin
                        errorMsg = null
                    }
                    +if (isLogin) "Register" else "Login"
                }
            }
        }
    }
}
