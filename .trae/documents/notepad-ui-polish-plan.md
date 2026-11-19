# NotePad 第二轮 UI 美化实施计划

> **执行要求：** 按任务顺序使用 TDD；先让新增测试因功能缺失而失败，再写最小实现并运行完整回归。不创建 Git 提交，除非用户另行要求。

**Goal：** 在现有原生 Android 架构上完成高、中优先级 UI 打磨，并加入真实可用的置顶与系统分享。

**Architecture：** 保留 `ListActivity + SimpleCursorAdapter + ContentProvider + SQLiteOpenHelper`。置顶通过数据库 v4 无损升级实现；标题和正文在编辑器内分区编辑并统一自动保存；不引入 AndroidX 或 Material 依赖。

**Tech Stack：** Java、Android View、原生 `DialogFragment/AlertDialog`、SQLite、旧版 instrumentation。

---

## Context

上一轮已完成卡片列表、搜索、摘要、待办、双主题和 Bottom Sheet。当前仍有标题/正文混排、两个溢出菜单、绝对秒级时间、无空状态和 FAB、搜索无清除入口等问题。用户选择本轮落地高、中优先级，并额外实现分享与置顶。

本轮不做复杂共享元素转场、滑动删除、平板双栏、格式工具栏、提醒或分类。左侧 `≡` 与 stylus 模型下载提示在项目源码中无匹配，属于系统或输入法界面，不使用私有 API 屏蔽。

## 1. 数据层：无损增加置顶

**文件：**
- `app/src/main/java/com/example/android/notepad/NotePad.java`
- `app/src/main/java/com/example/android/notepad/NotePadProvider.java`
- `app/src/androidTest/java/com/example/android/notepad/NotePadProviderTest.java`

- [ ] 先新增失败测试：默认 `is_pinned=0`；置顶更新可读写；较旧置顶笔记排在较新普通笔记前；置顶操作不改变 `modified`；v2→v4 保留标题、正文、时间及待办字段。
- [ ] 将原升级测试改为调用 `onUpgrade(mDb, 2, 4)`，同时断言 `is_todo/is_completed/is_pinned` 存在且默认值为 0。
- [ ] 新增契约与排序：

```java
public static final String COLUMN_NAME_IS_PINNED = "is_pinned";
public static final String DEFAULT_SORT_ORDER =
        "is_pinned DESC, modified DESC, _id DESC";
```

- [ ] 数据库版本从 3 升至 4；`onCreate()` 添加 `is_pinned INTEGER NOT NULL DEFAULT 0`，`onUpgrade()` 在 `oldVersion < 4` 时使用 `ALTER TABLE ADD COLUMN`，不得删表。无需为本地小列表新增索引。
- [ ] 在 projection map 和 insert 默认值中接入 `is_pinned`。置顶切换只写 `is_pinned`，不得调用会刷新修改时间的通用正文保存方法。
- [ ] 运行 Provider 测试，确认新增测试转绿且原测试无回归。

## 2. 列表行为：去重、相对时间、搜索清除与空状态

**文件：**
- `app/src/main/java/com/example/android/notepad/NotesList.java`
- `app/src/androidTest/java/com/example/android/notepad/NotePadActivityTest.java`

- [ ] 先添加失败测试：搜索清除恢复全部结果；无笔记与搜索无结果显示不同空态；正文首行作为空标题的显示标题且摘要跳过该行；标题与正文重复时摘要去重；旧时间不再显示秒数；FAB 能启动 `NoteEditor`；置顶笔记显示置顶标识。
- [ ] 在现有 `ViewBinder` 内统一计算标题和摘要：标题为空时取正文第一条非空行；正文与该标题相同或以该标题作为首行时，摘要从后续内容开始；仍为空则隐藏摘要。标题、正文都为空时显示“无标题”。
- [ ] 用 `formatRelativeTime(modified, now)` 支持“刚刚 / 今天 HH:mm / 昨天 / M月d日 / yyyy年M月d日”，并复用到列表绑定；移除 `SimpleDateFormat("yyyy-MM-dd HH:mm:ss")`。
- [ ] 搜索文本变化后同步更新清除按钮和空态文案；点击清除仅清空关键字并恢复结果。`onResume()` 按当前关键字重新查询，确保返回列表时标题、置顶和正文立即刷新。
- [ ] 视图回收时继续重置删除线、摘要可见性、CheckBox 监听器，并重置新增置顶标识，避免错位。
- [ ] 运行 Activity 测试，确认行为测试转绿。

## 3. 列表布局与主题资源

**文件：**
- `app/src/main/res/layout/notes_list.xml`
- `app/src/main/res/layout/noteslist_item.xml`
- `app/src/main/res/menu/list_options_menu.xml`
- `app/src/main/res/drawable/` 中现有卡片、搜索背景资源及必要的焦点/FAB 背景
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/attrs.xml`
- `app/src/main/res/values/styles.xml`

- [ ] 列表根布局改为 `FrameLayout`：上方胶囊搜索容器、中间 ListView、右下角 56dp `ImageButton` FAB。FAB 使用白色 `ADD` 图标、品牌色圆形背景和原生水波纹，列表底部预留空间。
- [ ] 移除顶部 `menu_add` 及对应菜单代码，FAB 复用原 `ACTION_INSERT` 启动逻辑；页面标题改为“笔记”。
- [ ] 搜索容器加入独立放大镜和 `search_clear` 按钮；无文字时清除按钮隐藏。EditText 背景透明，焦点状态使用品牌色 1.5dp 描边，不添加缩放大动画。
- [ ] 卡片改为 16dp 圆角、12dp 间距、16dp 内边距；标题单行省略，摘要最多两行；底部左侧时间、右侧待办 CheckBox；标题右侧显示小型置顶图标。
- [ ] 增加空状态线稿、标题和说明。空搜索结果显示“未找到相关笔记”，普通空状态提示点击右下角开始记录。
- [ ] 品牌色集中调整为 `#4CAF50`，强调色 `#2E7D32`；深色主题使用柔和绿色。新增 `noteBrandColor` 主题属性，避免 Java 中继续写死浅色品牌色。
- [ ] 保留原生按压水波纹，不实现低优先级卡片缩放或折叠顶栏。

## 4. 编辑器：标题正文分区、返回、保存和删除

**文件：**
- `app/src/main/java/com/example/android/notepad/NoteEditor.java`
- `app/src/main/res/layout/note_editor.xml`
- `app/src/main/res/menu/editor_options_menu.xml`
- `app/src/androidTest/java/com/example/android/notepad/NotePadActivityTest.java`

- [ ] 先添加失败测试：标题与正文分别保存；返回键触发 home；系统 `Edit title` alternative 菜单不再出现；删除前先显示确认框，取消不删除，确认才删除；恢复更改同时恢复标题和正文；保存图标随未保存状态改变。
- [ ] 将横格 `LinedEditText` 布局替换为普通标题 `note_title` 与正文 `note` 两个 EditText：标题 20sp 粗体单行，正文 16sp、1.6 倍行距；统一 16dp 左右边距，使用主题正文/提示色，不再绘制横线。
- [ ] 保存原始标题 `mOriginalTitle` 和正文 `mOriginalContent`，在 `onSaveInstanceState()` 中同时保留。`onPause()` 幂等保存两个字段，不再从正文截取标题；空标题由列表负责回退显示。
- [ ] 仅新笔记在标题和正文均为空且退出时删除；已有笔记清空内容不自动删除。增加 `mSkipSave`，保证确认删除和恢复后 `onPause()` 不再次写入。
- [ ] ActionBar 启用 home-as-up，处理 `android.R.id.home`；删除 `menu.addIntentOptions(...)`，不再出现系统“Edit title”溢出菜单。`TitleEditor` 保留给外部 Intent 使用。
- [ ] 标题/正文 TextWatcher 比较原始快照：未修改时保存图标置灰，有修改时使用品牌色；显式保存后更新快照并退出，自动保存不弹提示。
- [ ] 删除图标使用危险色；点击后通过原生 `AlertDialog` 显示“删除笔记？”及取消/删除按钮。

## 5. 更多 Bottom Sheet：置顶、分享、待办与恢复

**文件：**
- `app/src/main/java/com/example/android/notepad/NoteEditor.java`
- `app/src/main/java/com/example/android/notepad/LineIconDrawable.java`
- `app/src/main/res/layout/editor_more_sheet.xml`
- `app/src/main/res/drawable/bottom_sheet_light.xml`
- `app/src/main/res/drawable/bottom_sheet_dark.xml`
- `app/src/androidTest/java/com/example/android/notepad/NotePadActivityTest.java`

- [ ] 先添加失败测试：置顶面板状态正确、点击后数据库更新且修改时间不变；分享启动包含 `ACTION_SEND` 的 chooser，分享文本包含标题和正文；待办原有测试继续通过。
- [ ] 为 `LineIconDrawable` 增加 `BACK/PIN/SHARE/CLEAR` 等本轮实际使用的图标。
- [ ] Bottom Sheet 改为 24dp 顶部圆角，增加 32×4dp 拖拽手柄；按“置顶、分享、设为待办、恢复更改”排列，每行至少 56dp。置顶和待办右侧显示选中对勾。
- [ ] 置顶直接更新 Provider 并同步 `mIsPinned`；分享使用：

```java
Intent send = new Intent(Intent.ACTION_SEND);
send.setType("text/plain");
send.putExtra(Intent.EXTRA_SUBJECT, title);
send.putExtra(Intent.EXTRA_TEXT, title + "\n\n" + body);
startActivity(Intent.createChooser(send, getString(R.string.share_note)));
```

- [ ] 对无接收分享应用的情况显示中文错误提示，不让应用崩溃。
- [ ] API 20+ 使用 WindowInsets 增加底部安全区，低版本保留 16dp 底 padding；相关新 API 调用隔离在版本保护代码中。
- [ ] 用布局内轻量状态条模拟 Snackbar：置顶/待办操作后显示中文结果和“撤销”，撤销只还原对应状态；不引入 Material 依赖。

## 6. 中文文案与最终回归

**文件：**
- `app/src/main/res/values/strings.xml`
- 上述所有修改文件

- [ ] 将本轮可见页面和菜单统一为中文，包括“笔记、新建笔记、保存、删除、打开、复制、粘贴、编辑标题、标题、开始书写…、搜索标题或内容、置顶、分享笔记”等。
- [ ] 自检资源 ID、Cursor 列索引和测试辅助方法一致，不保留无效菜单或空实现。
- [ ] 运行完整测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

预期：`BUILD SUCCESSFUL`，所有 Provider 与 Activity 测试 0 失败。

- [ ] 安装 APK 时沿用 Android 17 测试环境需要的 `adb install -r --bypass-low-target-sdk-block`，不修改项目 minSdk/targetSdk。
- [ ] 实际检查浅色/深色：空状态、有笔记列表、搜索焦点与清除、长标题/摘要、相对时间、置顶标识、FAB、标题正文编辑、保存图标、删除确认、Bottom Sheet、分享面板和撤销。
- [ ] 检查横屏列表、长正文滚动和导航条安全区；清理所有带唯一前缀的验收数据并恢复检查前主题与系统设置。
