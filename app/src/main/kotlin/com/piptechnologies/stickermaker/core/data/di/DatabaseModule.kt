package com.piptechnologies.stickermaker.core.data.di

import android.content.Context
import androidx.room.Room
import com.piptechnologies.stickermaker.core.data.db.InstalledPackDao
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.db.OwnPackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Room database ("love.db") and DAO bindings. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LoveDb =
        Room.databaseBuilder(context, LoveDb::class.java, "love.db").build()

    @Provides
    fun provideInstalledPackDao(db: LoveDb): InstalledPackDao = db.installedPackDao()

    @Provides
    fun provideOwnPackDao(db: LoveDb): OwnPackDao = db.ownPackDao()
}
