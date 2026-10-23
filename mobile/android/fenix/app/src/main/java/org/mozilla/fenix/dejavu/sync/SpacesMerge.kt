/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.sync

import org.json.JSONObject

/** Kinds of records whose fields Dejavu edits, and keeps from being undone by an older change from another device. */
private val MERGED_KINDS = setOf(RecordKind.CONTAINER, RecordKind.SPACE, RecordKind.FOLDER, RecordKind.TAB)

/** Fields that order other records. Applying an incoming order already keeps what is only here. */
private val ORDER_FIELDS = setOf("children", "spaces", "essentials")

/**
 * The record to apply in place of [incoming], keeping the changes made here that it would undo. [base] is the
 * server's copy this device knew before, and [local] what Dejavu's data maps to now. A field only changed here keeps
 * its value here, and a field changed on both sides keeps the newer change: [localTime], when the item last changed
 * here, against [incomingTime], when the server got [incoming], both in server seconds. Without both times, the
 * incoming change wins, and so do deletions, orders, and records of tabs that are not pinned on both sides.
 *
 * The server's copy becomes [incoming] all the same, so the changes kept here are uploaded once it is applied.
 */
internal fun keepLocalChanges(
    incoming: SpacesRecord,
    base: SpacesRecord?,
    local: SpacesRecord?,
    incomingTime: Double?,
    localTime: Double?,
): SpacesRecord {
    val kind = incoming.kind ?: return incoming
    val theirs = incoming.data
    val before = base?.takeIf { !it.deleted && it.kind == kind }?.data
    val mine = local?.takeIf { !it.deleted && it.kind == kind }?.data
    if (kind !in MERGED_KINDS || theirs == null || before == null || mine == null) return incoming
    if (kind == RecordKind.TAB && (!base.isSynced || !local.isSynced || incoming.isNormalTab)) return incoming
    val newerHere = incomingTime != null && localTime != null && localTime > incomingTime
    val fields = (theirs.keys().asSequence() + before.keys().asSequence() + mine.keys().asSequence()).toSet()
    var merged: JSONObject? = null
    for (field in fields - ORDER_FIELDS) {
        val value = mine.opt(field)
        if (same(value, before.opt(field)) || same(value, theirs.opt(field))) continue
        if (newerHere || same(theirs.opt(field), before.opt(field))) {
            val data = merged ?: SyncJson.copy(theirs).also { merged = it }
            if (value == null) data.remove(field) else data.put(field, value)
        }
    }
    return merged?.let { SpacesRecord.of(incoming.id, kind, it) } ?: incoming
}

/** Whether two field values are the same, a missing field being the same as `null`. */
private fun same(first: Any?, second: Any?): Boolean = SyncJson.canonical(first) == SyncJson.canonical(second)
