/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.architecture.blueprints.todoapp.addedittask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.android.architecture.blueprints.todoapp.LiveDataTestUtil.getValue
import com.example.android.architecture.blueprints.todoapp.R.string
import com.example.android.architecture.blueprints.todoapp.ViewModelScopeMainDispatcherRule
import com.example.android.architecture.blueprints.todoapp.assertSnackbarMessage
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineContext
import org.hamcrest.Matchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the implementation of [AddEditTaskViewModel].
 */
@ObsoleteCoroutinesApi
class AddEditTaskViewModelTest {

    // Subject under test
    private lateinit var addEditTaskViewModel: AddEditTaskViewModel

    // Use a fake repository to be injected into the viewmodel
    private lateinit var tasksRepository: FakeRepository

    // Set the main coroutines dispatcher for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesMainDispatcherRule = ViewModelScopeMainDispatcherRule()

    // Executes each task synchronously using Architecture Components.
    @get:Rule var instantExecutorRule = InstantTaskExecutorRule()

    // A CoroutineContext that can be controlled from tests
    private val testContext = TestCoroutineContext()

    private val task = Task("Title1", "Description1")

    @Before
    fun setupViewModel() {
        // We initialise the repository with no tasks
        tasksRepository = FakeRepository()

        // Create class under test
        addEditTaskViewModel = AddEditTaskViewModel(tasksRepository, testContext)
    }

    @ObsoleteCoroutinesApi
    @Test
    fun saveNewTaskToRepository_showsSuccessMessageUi() {
        val newTitle = "New Task Title"
        val newDescription = "Some Task Description"
        (addEditTaskViewModel).apply {
            title.value = newTitle
            description.value = newDescription
        }
        addEditTaskViewModel.saveTask()

        // Execute pending coroutines actions
        testContext.triggerActions()

        val newTask = tasksRepository.tasksServiceData.values.first()

        // Then a task is saved in the repository and the view updated
        assertThat(newTask.title, `is`(newTitle))
        assertThat(newTask.description, `is`(newDescription))
    }

    @Test
    fun loadTasks_loading() {
        // Load the task in the viewmodel
        addEditTaskViewModel.start(task.id)

        // Then progress indicator is shown
        assertTrue(getValue(addEditTaskViewModel.dataLoading))

        // Execute pending coroutines actions
        testContext.triggerActions()

        // Then progress indicator is hidden
        assertFalse(getValue(addEditTaskViewModel.dataLoading))
    }

    @Test
    fun loadTasks_taskShown() {
        // Add task to repository
        tasksRepository.addTasks(task)

        // Load the task with the viewmodel
        addEditTaskViewModel.start(task.id)

        // Execute pending coroutines actions
        testContext.triggerActions()

        // Verify a task is loaded
        assertEquals(getValue(addEditTaskViewModel.title), task.title)
        assertEquals(getValue(addEditTaskViewModel.description), task.description)
        assertEquals(getValue(addEditTaskViewModel.dataLoading), false)
    }

    @ObsoleteCoroutinesApi
    @Test
    fun saveNewTaskToRepository_emptyTitle_error() {
        saveTaskAndAssertSnackbarError("", "Some Task Description")
    }

    @ObsoleteCoroutinesApi
    @Test
    fun saveNewTaskToRepository_nullTitle_error() {
        saveTaskAndAssertSnackbarError(null, "Some Task Description")
    }
    @ObsoleteCoroutinesApi
    @Test
    fun saveNewTaskToRepository_emptyDescription_error() {
        saveTaskAndAssertSnackbarError("Title", "")
    }

    @ObsoleteCoroutinesApi
    @Test
    fun saveNewTaskToRepository_nullDescription_error() {
        saveTaskAndAssertSnackbarError("Title", null)
    }

    @ObsoleteCoroutinesApi
    @Test
    fun saveNewTaskToRepository_nullDescriptionNullTitle_error() {
        saveTaskAndAssertSnackbarError(null, null)
    }

    @ObsoleteCoroutinesApi
    @Test
    fun saveNewTaskToRepository_emptyDescriptionEmptyTitle_error() {
        saveTaskAndAssertSnackbarError("", "")
    }

    private fun saveTaskAndAssertSnackbarError(title: String?, description: String?) {
        (addEditTaskViewModel).apply {
            this.title.value = title
            this.description.value = description
        }

        // When saving an incomplete task
        addEditTaskViewModel.saveTask()

        // Then the snackbar shows an error
        assertSnackbarMessage(addEditTaskViewModel.snackbarMessage, string.empty_task_message)
    }
}
