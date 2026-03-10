import { useState } from 'react'
import { useTasks } from './hooks/useTasks'
import { TaskSection } from './components/TaskSection'
import { TaskForm } from './components/TaskForm'
import './App.css'

function App() {
  const { tasks, isLoading, error, createTask, completeTask, deleteTask } = useTasks()
  const [showForm, setShowForm] = useState(false)

  return (
    <div className="app">
      <header className="app-header">
        <h1>Task Reloader</h1>
        <p className="subtitle">A completion-driven recurring task scheduler</p>
        <button onClick={() => setShowForm((v) => !v)}>
          {showForm ? '닫기' : '+ 새 Task'}
        </button>
      </header>

      <main className="app-main">
        {showForm && (
          <TaskForm
            onSubmit={async (req) => {
              const ok = await createTask(req)
              if (ok) setShowForm(false)
              return ok
            }}
            onCancel={() => setShowForm(false)}
          />
        )}

        {error && <p className="app-error">{error}</p>}

        {isLoading ? (
          <p className="app-loading">불러오는 중...</p>
        ) : (
          <TaskSection
            tasks={tasks}
            onComplete={completeTask}
            onDelete={deleteTask}
          />
        )}
      </main>
    </div>
  )
}

export default App

