# Task Manager

An Android productivity app for managing tasks and deadlines. Tasks have due dates, priorities and a status, can carry a checklist of steps, and are grouped by colored categories. Everything is stored locally with a Room database, so it works offline and survives restarts.

Built as **Task 6** of the Auspify Technologies Android Developer Internship.

## Features

- Create and edit tasks with a title, notes, due date, priority, status and category
- **Checklists:** add steps to any task, tick them off, and watch the task's status follow along
- **Colored categories:** four ready-made ones (School, Home, Work, Personal), plus your own with a name and a choice of eight colors
- **Today and Upcoming tabs:** overdue tasks appear under Today in red, and later or undated tasks under Upcoming
- **Status management:** tap the circle on a card to move a task through To do, In progress and Done
- **Progress ring** showing how many of all your tasks are done
- **Search** across task titles and notes, with an expanding search field
- **Filter and sort:** filter by category, status and priority, and sort by due date, priority or newest. Done tasks always sink to the bottom
- **Swipe to delete** with an Undo option that restores the task and its whole checklist
- Light purple design with soft cards, and animations for the list, the status circle, the checklist, search and the editor
- Search, filters and the editor's contents all survive screen rotation

## Tech Stack

- Kotlin
- Android Studio, XML layouts, View Binding
- Room database (with KSP), using relations, foreign keys and transactions
- ViewModel with Kotlin Coroutines, Flow and StateFlow
- RecyclerView with ListAdapter and DiffUtil
- ItemTouchHelper for swipe gestures
- Material Components (date picker, bottom sheet, dialogs, Snackbar)
- Property animations and layout animations

## Database Design

Three linked tables:

| Table | Holds | Relationship |
|---|---|---|
| `categories` | name, color | Deleting a category leaves its tasks uncategorized |
| `tasks` | title, notes, due date, status, priority, category, created time | Belongs to one category |
| `subtasks` | title, done flag, position | Deleting a task deletes its steps |

- A task is loaded with its category and checklist in a single query, so the list never makes a separate query per card.
- Searching, filtering, tab selection and sorting are all parameters of **one query**, so Room re-runs it and the list updates by itself when any choice changes.
- Saving a task and its checklist happens in one transaction, so it can't be left half saved. Undo uses a transaction to restore a deleted task and its steps together.
- The four default categories are added once, the first time the database is created.

## Architecture

- `TaskViewModel` holds the filters, search state and progress for the main screen
- `EditTaskViewModel` holds the editor's draft, so rotating the phone loses nothing
- Screens only collect state and draw it, and they hold no filter state of their own

## Screenshots

| Today | Upcoming | Checklist |
|---|---|---|
| ![Today](Screenshots/01_today.jpg) | ![Upcoming](Screenshots/02_upcoming.jpg) | ![Checklist](Screenshots/03_checklist.jpg) |

| Task editor | New category | Filter and sort |
|---|---|---|
| ![Editor](Screenshots/04_editor.jpg) | ![New category](Screenshots/05_new_category.jpg) | ![Filter](Screenshots/06_filter.jpg) |

| Search | Swipe to delete | Undo | Empty state |
|---|---|---|---|
| ![Search](Screenshots/07_search.jpg) | ![Swipe](Screenshots/08_swipe.jpg) | ![Undo](Screenshots/09_undo.jpg) | ![Empty](Screenshots/10_empty.jpg) |

## Known Limitations

- Categories can be created but not renamed or deleted
- Due dates are dates only, with no time of day, and there are no reminders
- The Today and Upcoming split refreshes when you change tab or filter, not at midnight while the app stays open
- Leaving the editor with the back button discards unsaved changes without asking

## How to Run

1. Clone the repository
2. Open it in Android Studio
3. Let Gradle sync
4. Run on an emulator or a device with Android 10 (API 29) or higher

The app works fully offline and needs no accounts or API keys.

## Author

Ameer, Auspify Android Developer Intern