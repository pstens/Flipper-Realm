/*
 * Copyright 2019 KG Soft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kgurgul.flipper

import io.realm.RealmConfiguration
import io.realm.RealmFieldType
import io.realm.Sort
import io.realm.internal.OsList
import io.realm.internal.OsResults
import io.realm.internal.OsSharedRealm
import io.realm.internal.Row
import io.realm.internal.core.DescriptorOrdering
import io.realm.internal.core.QueryDescriptor
import java.text.SimpleDateFormat
import java.util.*

internal object RealmHelper {

    private fun getSharedRealm(realmConfiguration: RealmConfiguration): OsSharedRealm {
        return OsSharedRealm.getInstance(realmConfiguration)
    }

    fun getTableNames(realmConfiguration: RealmConfiguration): List<String> {
        return getSharedRealm(realmConfiguration)
            .use { sharedRealm ->
                val tableNames = mutableListOf<String>()
                for (i in 0 until sharedRealm.size()) {
                    tableNames.add(sharedRealm.getTableName(i.toInt()))
                }
                tableNames
            }
    }

    fun getTableColumns(
        realmConfiguration: RealmConfiguration,
        tableName: String
    ): List<RealmColumnInfo> {
        return getSharedRealm(realmConfiguration)
            .use { sharedRealm ->
                val columnNames = mutableListOf<RealmColumnInfo>()
                val table = sharedRealm.getTable(tableName)
                for (i in 0 until table.columnCount) {
                    columnNames.add(
                        RealmColumnInfo(
                            table.getColumnName(i),
                            table.getColumnType(i).name,
                            table.isColumnNullable(i)
                        )
                    )
                }
                columnNames
            }
    }

    fun getRows(
        realmConfiguration: RealmConfiguration,
        tableName: String,
        start: Int,
        count: Int,
        order: String?,
        reverse: Boolean
    ): List<List<Any>> {
        return getSharedRealm(realmConfiguration)
            .use { sharedRealm ->
                val valueList = mutableListOf<List<Any>>()
                val table = sharedRealm.getTable(tableName)
                val queryOrder = DescriptorOrdering().apply {
                    if (order != null) {
                        val sortOrder = if (reverse) Sort.DESCENDING else Sort.ASCENDING
                        // TODO: find a way to pass the proper proxy here
                        appendSort(QueryDescriptor.getInstanceForSort(null, table, order, sortOrder))
                    }
                }
                val osResults = OsResults.createFromQuery(sharedRealm, table.where(), queryOrder)
                for (i in start until osResults.size()) {
                    val rawCheckedRow = osResults.getUncheckedRow(i.toInt())
                    val rowValues = mutableListOf<Any>()
                    for (j in 0 until rawCheckedRow.columnCount) {
                        rowValues.add(getRowData(rawCheckedRow, j))
                    }
                    valueList.add(rowValues)
                    if (valueList.size == count) {
                        break
                    }
                }
                valueList
            }
    }

    fun getRowsCount(realmConfiguration: RealmConfiguration, tableName: String): Long {
        return getSharedRealm(realmConfiguration)
            .use { sharedRealm ->
                sharedRealm.getTable(tableName).size()
            }
    }

    private fun getRowData(row: Row, index: Long): Any {
        return when (row.getColumnType(index)) {
            RealmFieldType.INTEGER -> {
                if (row.isNull(index)) {
                    NULL
                } else {
                    row.getLong(index).toString()
                }
            }
            RealmFieldType.BOOLEAN -> {
                if (row.isNull(index)) {
                    NULL
                } else {
                    row.getBoolean(index)
                }
            }
            RealmFieldType.STRING -> {
                if (row.isNull(index)) {
                    NULL
                } else {
                    row.getString(index)
                }
            }
            RealmFieldType.BINARY -> {
                if (row.isNull(index)) {
                    NULL
                } else {
                    row.getBinaryByteArray(index).toString()
                }
            }
            RealmFieldType.DATE -> {
                if (row.isNull(index)) {
                    NULL
                } else {
                    formatDate(row.getDate(index))
                }
            }
            RealmFieldType.FLOAT -> {
                if (row.isNull(index)) {
                    NULL
                } else {
                    when (val aFloat = row.getFloat(index)) {
                        Float.NaN -> "NaN"
                        Float.POSITIVE_INFINITY -> "Infinity"
                        Float.NEGATIVE_INFINITY -> "-Infinity"
                        else -> aFloat.toString()
                    }
                }
            }
            RealmFieldType.DOUBLE -> {
                if (row.isNull(index)) {
                    NULL
                } else {
                    when (val aDouble = row.getDouble(index)) {
                        Double.NaN -> "NaN"
                        Double.POSITIVE_INFINITY -> "Infinity"
                        Double.NEGATIVE_INFINITY -> "-Infinity"
                        else -> aDouble.toString()
                    }
                }
            }
            RealmFieldType.OBJECT -> {
                if (row.isNullLink(index)) {
                    NULL
                } else {
                    row.getLink(index).toString()
                }
            }
            RealmFieldType.LIST -> {
                formatList(row.getModelList(index))
            }
            RealmFieldType.INTEGER_LIST,
            RealmFieldType.FLOAT_LIST,
            RealmFieldType.DOUBLE_LIST,
            RealmFieldType.BOOLEAN_LIST,
            RealmFieldType.BINARY_LIST,
            RealmFieldType.DATE_LIST,
            RealmFieldType.STRING_LIST -> {
                if (row.isNullLink(index)) {
                    NULL
                } else {
                    val columnType = row.getColumnType(index)
                    formatValueList(row.getValueList(index, columnType), columnType)
                }
            }
            else -> "[FLIPPER_UNKNOWN_VALUE]"
        }
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG)
        return "${sdf.format(date)} (${date.time})"
    }

    private fun formatList(osList: OsList): String {
        val sb = StringBuilder(osList.targetTable?.name ?: "")
        val size = osList.size()
        sb.append("{")
        for (i in 0 until size) {
            sb.append(osList.getUncheckedRow(i).index)
            sb.append(',')
        }
        if (size > 0) {
            sb.setLength(sb.length - 1)
        }
        sb.append("}")
        return sb.toString()
    }

    private fun formatValueList(osList: OsList, columnType: RealmFieldType): String {
        val sb = StringBuilder(columnType.name)
        val size = osList.size()
        sb.append("{")
        for (i in 0 until size) {
            sb.append(osList.getValue(i))
            sb.append(',')
        }
        if (size > 0) {
            sb.setLength(sb.length - 1)
        }
        sb.append("}")
        return sb.toString()
    }

    data class RealmColumnInfo(
        val name: String,
        val type: String,
        val isNullable: Boolean
    )

    private const val NULL = "[null]"
}