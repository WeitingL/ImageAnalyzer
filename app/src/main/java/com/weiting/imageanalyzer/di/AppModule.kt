package com.weiting.imageanalyzer.di

import com.weiting.imageanalyzer.data.ContentResolverImageRepository
import com.weiting.imageanalyzer.data.GeminiNanoShareCheckRepository
import com.weiting.imageanalyzer.data.ImageRepository
import com.weiting.imageanalyzer.data.ShareCheckRepository
import com.weiting.imageanalyzer.ui.home.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<ImageRepository> { ContentResolverImageRepository(androidContext()) }

    // Swap this binding to try a different on-device backend; nothing above it changes.
    single<ShareCheckRepository> { GeminiNanoShareCheckRepository() }

    viewModel { HomeViewModel(get(), get()) }
}
