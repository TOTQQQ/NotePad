# NotePad 现代化 UI 重构实施计划

## Context

当前列表和编辑器仍接近 Holo 原生样式，缺少卡片层级、统一图标和触屏友好的操作面板。本次在不更换 Java/Android View 架构、不引入 AndroidX/Material、不修改数据库结构的前提下，参考印象笔记与有道云笔记重构列表页和编辑页，并保留搜索、待办、自动保存及手动浅色/深色主题。

Android 使用 `dp/sp` 实现需求中的 `px` 视觉尺寸，保证不同密度设备比例一致；不另做重复 HTML Demo，直接通过模拟器截图验收高保真效果。

## 设计规范

- 品牌色：黄绿色 `#8BC34A`，用于新建、选中态和关键确认操作。
- 浅色页面：`#F7F8FA`；卡片/输入区域：白色；深色页面使用对应暗色表面，保留原有主题偏好。
- 文本：标题 `#1A1A1A`、正文 `#333333`、摘要 `#666666`、辅助信息 `#999999`。
- 卡片：12dp 圆角、16dp 水平间距、10dp 卡片间距、1dp 向下极淡阴影；取消列表分隔线。
- 搜索框：40dp 高、20dp 圆角、灰色背景、左侧放大镜，无原生下划线。
- 列表标题：17sp 粗体单行；摘要：14sp、最多两行；时间：12sp 右下对齐。
- 编辑器：20sp、1.6 倍行距、舒适留白；标题栏直接显示当前标题，新建时显示 `New note`。
- 底部操作面板：顶部 16dp 圆角，选项行至少 56dp，左侧线性图标、右侧待办选中标记。
- 图标：统一线宽和端点，不继续使用模糊 PNG；不增加无实际功能的 FAB 或富文本按钮。

## 文件与实施步骤

### 1. 先补充行为测试并确认 RED

修改 `app/src/androidTest/java/com/example/android/notepad/NotePadActivityTest.java`：

- 新增 `testListItemShowsContentSummary()`：插入包含多行正文的笔记，动态查找 `note_summary`，断言摘要存在、内容正确且多行空白被整理为单行空格。
- 将 `testEditorCanMarkNoteAsTodo()` 改为先调用 `menu_more`，断言底部面板及 `todo_sheet_option` 出现，再点击整行并验证 Provider 中 `is_todo=1`。
- 新增取消待办测试，确认通过底部面板取消后同时清除 `is_completed`。
- 测试继续使用动态资源 ID，确保 RED 是功能缺失断言，而非资源编译错误。

运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.android.notepad.NotePadActivityTest
```

预期：摘要和底部面板相关测试失败，其余原有测试保持通过。

### 2. 建立主题、尺寸和统一图标资源

新增必要的资源文件：

- `res/values/colors.xml`、`dimens.xml`、`attrs.xml`、`styles.xml`
- `res/drawable/note_card_light.xml`、`note_card_dark.xml`
- `res/drawable/search_background_light.xml`、`search_background_dark.xml`
- `res/drawable/bottom_sheet_light.xml`、`bottom_sheet_dark.xml`

定义继承 `Theme.Holo.Light` 和 `Theme.Holo` 的应用主题，设置页面背景、ActionBar 背景、粗体标题及品牌高亮。卡片使用圆角 `layer-list` 形成极淡偏移阴影，不依赖 API 21 的 elevation。

新增 `LineIconDrawable.java`，用 `Canvas/Paint` 绘制本页实际需要的笔记、搜索、新建、保存、删除、更多、待办、恢复和勾选图标。它兼容 minSdk 11，并用于 ActionBar 菜单、搜索框、待办圆形复选框和底部面板。

### 3. 重构笔记列表页

修改 `NotesList.java`、`notes_list.xml`、`noteslist_item.xml`：

- 在 `PROJECTION` 末尾追加 `COLUMN_NAME_NOTE`，保留现有列索引；将正文映射到 `note_summary`。
- 保留 `queryNotes()`、搜索 `TextWatcher`、完成状态更新和删除线逻辑。
- 摘要仅压缩换行及连续空白，由 `TextView` 的 `maxLines=2` 和 `ellipsize=end` 负责截断。
- 列表项改为白色/暗色圆角卡片：左侧自定义待办圆圈，中间标题与摘要，右下时间；无分隔线，以卡片外边距形成间距。
- 搜索框改为圆角背景并设置线性放大镜；ListView 增加统一留白和底部裁剪保护。
- 应用自定义主题，并为新建、主题入口和 Logo 设置统一颜色；保留原有菜单 ID 与点击行为。

### 4. 重构笔记编辑页和底部操作面板

修改 `NoteEditor.java`、`note_editor.xml`、`editor_options_menu.xml`、`strings.xml`，新增 `layout/editor_more_sheet.xml`：

- 编辑器根布局使用页面背景，正文保持透明，设置 20sp、1.6 倍行距及主题化文字/提示色；保留 `LinedEditText`，不添加遮挡内容的悬浮按钮。
- `onResume()` 编辑已有笔记时直接 `setTitle(title)`，不再添加 `Edit:` 前缀；新建仍显示 `New note`。
- 保存、删除使用统一 ActionBar 图标；以显式 `menu_more` 替代原生 `menu_todo` 溢出项。
- 点击 `menu_more` 创建原生 `Dialog`：去标题、透明窗口背景、`Gravity.BOTTOM`、宽度 MATCH_PARENT，内容使用顶部圆角面板。
- 面板包含“设为待办”和按原条件显示的“恢复更改”。每次打开同步 `mIsTodo`；点击待办整行后复用 `updateNote()` 立即保存，取消待办仍清除 `mIsCompleted`。
- 删除 `onPrepareOptionsMenu()` 对已移除 `menu_todo` 的引用，恢复功能仍调用现有 `cancelNote()`。

### 5. GREEN、完整回归与视觉验收

先重跑 `NotePadActivityTest`，确认新增测试和原有搜索、时间戳、待办、主题测试全部通过；再执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

预期：Pixel 5 与 Pixel 9a 全部测试通过，Provider 数据库及迁移测试不受影响。

最后在模拟器中创建唯一前缀的临时演示数据，检查：

- 浅色/深色列表的卡片层级、圆角、阴影、摘要截断、时间和搜索栏。
- 待办圆圈未选/选中状态、标题删除线、搜索结果内勾选后的刷新。
- 编辑器标题、正文行高、横线可读性、保存/删除图标及底部面板圆角和大点击区域。
- 长标题、两行以上正文、空正文和横竖屏布局。

清理临时数据并恢复检查前主题偏好，不生成多余项目文件。
