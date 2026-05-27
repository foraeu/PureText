package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.PureTextViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PureTextViewModelTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun testInitialState() = runTest {
        val viewModel = PureTextViewModel(application)
        assertEquals(0, viewModel.tabs.value.size)
        assertEquals(null, viewModel.activeTabUri.value)
    }
}
