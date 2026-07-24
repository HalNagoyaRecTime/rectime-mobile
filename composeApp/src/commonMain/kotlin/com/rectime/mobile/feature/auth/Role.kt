package com.rectime.mobile.feature.auth

enum class Role(val label: String) {
    Student("生徒"),
    ClassRepresentative("クラス代表者"),
    CompetitionStaff("競技担当"),
    RecCommitteeHq("レク委員本部"),
    Teacher("教官");

    companion object {
        fun fromWireValue(value: String?): Role? = entries.firstOrNull { it.name == value }
    }
}
