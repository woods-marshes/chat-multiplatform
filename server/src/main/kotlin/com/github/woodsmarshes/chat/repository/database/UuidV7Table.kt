package com.github.woodsmarshes.chat.repository.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import kotlin.uuid.Uuid

open class UuidV7Table(name: String = "", columnName: String = "id") : IdTable<Uuid>(name) {

    final override val id: Column<EntityID<Uuid>> = uuid(columnName).clientDefault { Uuid.generateV7() }.entityId()

    final override val primaryKey = PrimaryKey(id)
}