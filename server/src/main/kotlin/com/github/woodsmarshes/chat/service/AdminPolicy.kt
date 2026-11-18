package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.UserError

/**
 * Admin accounts are immutable through the HTTP API, peers and self alike: an
 * admin may not ban, demote, reset the password of, or revoke the sessions of
 * any admin. [requireRoleGrant] closes the other direction, so no API call can
 * grant ADMIN either — self-demotion included. Admin identity therefore only
 * ever changes through `admin.emails` on startup or direct DB work, so a rogue
 * or compromised admin can neither lock peers out nor mint more admins.
 *
 * Every role-changing entry point must call both helpers. The legacy
 * `PUT /v1/users/{id}/role` route in [UserService] and the admin routes in
 * [AdminService] share this policy; an unchecked second implementation is
 * exactly how the rule would silently regress.
 */
internal fun requireMutableTarget(target: User): Result<Unit, UserError> =
    if (target.role == UserRole.ADMIN) Err(UserError.PermissionDenied) else Ok(Unit)

/** Admin identity is never granted through the API. */
internal fun requireRoleGrant(newRole: UserRole): Result<Unit, UserError> =
    if (newRole == UserRole.ADMIN) Err(UserError.PermissionDenied) else Ok(Unit)
