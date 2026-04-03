package com.shihuaidexianyu.money.ui.common

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.domain.model.AccountGroupType

fun AccountEntity.toAccountOptionUiModel(): AccountOptionUiModel {
    return AccountOptionUiModel(
        id = id,
        name = name,
        groupType = AccountGroupType.fromValue(groupType),
    )
}

fun List<AccountEntity>.toAccountOptionUiModels(): List<AccountOptionUiModel> {
    return map(AccountEntity::toAccountOptionUiModel)
}

