package com.github.woodsmarshes.web.state

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.web.koinInject
import kotlinx.coroutines.flow.combine
import react.Context
import react.FC
import react.PropsWithChildren
import react.createContext
import react.use
import react.useEffectOnce
import react.useState

data class Status(
    val user: User?,
    val isLoggedIn: Boolean
)
val Context: Context<Status> = createContext(Status(
    user = null,
    isLoggedIn = false
))

fun useCurrentContext(): Status = use(Context)

/**
 * 在组件树根部订阅一次 DataStore 的 user Flow 和 jwtToken Flow，
 * 通过 [Context]下发给整棵组件树。
 */
val ContextProvider = FC<PropsWithChildren> { props ->
    var status by useState(Status(
        user = null,
        isLoggedIn = false
    ))

    useEffectOnce {
        combine(
            koinInject<AuthTokenDataSource>().jwtToken,
            koinInject<UserSettingDataSource>().user
        ) { token, u ->
            Status(
                user = u,
                isLoggedIn = token != null
            )
        }.collect { newStatus ->
            status = newStatus // 3. 统一更新状态
        }
    }

    Context.Provider {
        value = status
        +props.children
    }
}
