/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.data.source

import com.example.android.architecture.blueprints.todoapp.data.source.remote.NetworkTask
import com.example.android.architecture.blueprints.todoapp.data.source.remote.TaskStatus

class FakeNetworkDataSource(
    var tasks: MutableList<NetworkTask>? = mutableListOf()
) : NetworkDataSource {
    override suspend fun loadTasks() = tasks ?: throw Exception("Task list is null")

    override suspend fun getTask(taskId: String) = tasks?.firstOrNull { it.id == taskId }

    override suspend fun saveTask(task: NetworkTask) {
        tasks?.add(task)
    }

    override suspend fun completeTask(taskId: String) {
        tasks?.firstOrNull { it.id == taskId }?.let {
            deleteTask(it.id)
            saveTask(it.copy(status = TaskStatus.COMPLETE))
        }
    }

    override suspend fun activateTask(taskId: String) {
        tasks?.firstOrNull { it.id == taskId }?.let {
            deleteTask(it.id)
            saveTask(it.copy(status = TaskStatus.ACTIVE))
        }
    }

    override suspend fun clearCompletedTasks() {
        tasks?.removeIf { it.status == TaskStatus.COMPLETE }
    }

    override suspend fun deleteAllTasks() {
        tasks?.clear()
    }

    override suspend fun deleteTask(taskId: String) {
        tasks?.removeIf { it.id == taskId }
    }
}
