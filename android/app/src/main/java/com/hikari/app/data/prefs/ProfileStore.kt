package com.hikari.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "hikari_profile")

private val NAME_KEY = stringPreferencesKey("name")
private val NICKNAME_KEY = stringPreferencesKey("nickname")
private val BIO_KEY = stringPreferencesKey("bio")
private val AVATAR_PATH_KEY = stringPreferencesKey("avatar_path")

const val BIO_MAX_LENGTH = 180

@Singleton
class ProfileStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    val name: Flow<String> = ctx.profileDataStore.data.map { it[NAME_KEY].orEmpty() }
    val nickname: Flow<String> = ctx.profileDataStore.data.map { it[NICKNAME_KEY].orEmpty() }
    val bio: Flow<String> = ctx.profileDataStore.data.map { it[BIO_KEY].orEmpty() }
    val avatarPath: Flow<String?> = ctx.profileDataStore.data.map { it[AVATAR_PATH_KEY] }

    suspend fun setName(value: String) {
        ctx.profileDataStore.edit { it[NAME_KEY] = value.trim().take(40) }
    }

    suspend fun setNickname(value: String) {
        ctx.profileDataStore.edit { it[NICKNAME_KEY] = value.trim().lowercase() }
    }

    suspend fun setBio(value: String) {
        ctx.profileDataStore.edit { it[BIO_KEY] = value.take(BIO_MAX_LENGTH) }
    }

    suspend fun setAvatarPath(path: String?) {
        ctx.profileDataStore.edit {
            if (path == null) it.remove(AVATAR_PATH_KEY)
            else it[AVATAR_PATH_KEY] = path
        }
    }
}
