# NotePad 主题美化与待办功能实施计划

## Context

现有 NotePad 已支持笔记条目的修改时间和按标题/内容搜索。接下来在不引入 AndroidX、Room 或独立待办页面的前提下，增加可记忆的浅色/深色主题，改善列表与编辑器视觉，并允许把任意笔记设为待办、在列表中勾选完成。数据库升级必须保留已有笔记。

## 实施方案

### 1. 先补充失败测试

修改现有测试，不新建无关测试文件：

- `app/src/androidTest/java/com/example/android/notepad/NotePadProviderTest.java`
  - 验证普通笔记的 `is_todo`、`is_completed` 默认均为 0，并可查询、更新。
  - 在隔离测试数据库中构造 v2 表和旧数据，直接调用 `DatabaseHelper.onUpgrade`，验证升级到 v3 后标题、正文、时间戳保留，新列默认为 0。
- `app/src/androidTest/java/com/example/android/notepad/NotePadActivityTest.java`
  - 验证编辑器菜单能把普通笔记设为待办，重新查询后状态仍存在。
  - 验证待办项显示复选框，点击后完成状态写回 Provider，标题显示/清除删除线，普通笔记不显示复选框。
  - 验证主题菜单切换后 SharedPreferences 状态改变；测试结束恢复原主题。
- 先运行新增测试，确认因字段、菜单和复选框尚不存在而按预期失败。

### 2. 原地升级待办数据结构

- `app/src/main/java/com/example/android/notepad/NotePad.java`
  - 增加 `COLUMN_NAME_IS_TODO = "is_todo"` 和 `COLUMN_NAME_IS_COMPLETED = "is_completed"`，INTEGER 0/1 语义。
- `app/src/main/java/com/example/android/notepad/NotePadProvider.java`
  - `DATABASE_VERSION` 升至 3，projection map 注册新列。
  - `onCreate` 为两列设置 `NOT NULL DEFAULT 0`，`insert` 对缺省值补 0。
  - `onUpgrade` 用 `ALTER TABLE notes ADD COLUMN` 添加两列，删除 DROP/重建逻辑，保证旧数据不丢失。
  - 复用现有 query/update/notifyChange，不新增 Provider URI。

### 3. 编辑器支持“设为待办”

- `app/src/main/java/com/example/android/notepad/NoteEditor.java`
  - PROJECTION 加入待办字段，在 `onResume` 从 Cursor 加载状态。
  - 菜单切换时更新内存状态并立即通过现有 `updateNote` 保存；取消待办时同时清除完成状态。
  - `updateNote` 统一写入两个字段，覆盖保存菜单、自动保存及新建笔记路径。
- `app/src/main/res/menu/editor_options_menu.xml`
  - 增加 checkable 的“设为待办”菜单项，`onPrepareOptionsMenu` 同步勾选状态。

### 4. 列表显示和完成待办

- `app/src/main/res/layout/noteslist_item.xml`
  - 改为“复选框 + 标题/时间”水平结构，统一 16dp 左右间距和合理上下间距。
- `app/src/main/java/com/example/android/notepad/NotesList.java`
  - PROJECTION 增加待办字段，ViewBinder 仅对待办显示 CheckBox；普通笔记设为 GONE。
  - 每次绑定都重置勾选、标题删除线和监听器，避免 ListView 回收导致串状态。
  - 点击复选框按 `_ID` 更新 `is_completed`，随后保留当前搜索词刷新 Cursor；点击条目其余区域仍打开原编辑器。

### 5. 浅色/深色主题与 UI 美化

- `NotesList.java`、`NoteEditor.java`
  - 在 `super.onCreate` 前读取同一个 SharedPreferences 配置，分别应用 `Theme.Holo.Light` 或 `Theme.Holo`。
  - 列表菜单增加主题切换，保存偏好并调用 `recreate()`；菜单标题根据当前主题显示“深色主题”或“浅色主题”。编辑器下次打开时应用同一主题。
- `app/src/main/AndroidManifest.xml`
  - 移除 NoteEditor 固定的 `Theme.Holo.Light`，避免覆盖运行时主题。
- `notes_list.xml`、`noteslist_item.xml`、`note_editor.xml`
  - 使用主题背景及主/次文字颜色，优化搜索框内外边距、列表分隔线、条目标题与时间层级。
  - 编辑器增加舒适页边距、合适字号和行间距；`LinedEditText` 横线颜色由当前文字颜色派生并降低透明度，替代固定蓝色，保证深浅主题均可读。
- `app/src/main/res/values/strings.xml`
  - 增加“设为待办”“深色主题”“浅色主题”等必要文案。

## 验证

1. 运行新增 Provider/Activity 测试，确认从 RED 转为 GREEN。
2. 执行 `./gradlew.bat :app:connectedDebugAndroidTest`，在两台模拟器运行完整测试集，确保原时间戳、搜索及 Provider 测试全部通过。
3. 安装并启动应用，人工检查浅色/深色下列表、搜索框、编辑器、待办复选框及删除线；旋转或重开页面确认主题与待办状态保留。
4. 用现有 v2 数据升级启动，确认原笔记仍在，普通笔记默认不显示待办框。
