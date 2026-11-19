# 笔记时间修正与分类功能实施计划

## 一、目标摘要

1. 修正列表更新时间与系统时间相差 8 小时的问题：保存仍使用系统当前毫秒值，列表统一按 `Asia/Shanghai` 显示 `yyyy-MM-dd HH:mm:ss`，不直接给时间戳加 8 小时。
2. 增加完整可用的预设分类功能：编辑器设置分类、列表卡片显示分类、首页按分类筛选，并支持与标题/正文搜索组合使用。
3. 数据库从 v4 无损升级到 v5，已有笔记、时间、待办、完成及置顶状态全部保留。

## 二、现状与根因

- 截图中笔记显示 `2026-11-18 10:23:03`，Windows 系统为 `2026-11-18 18:23`，正好相差 8 小时。
- [NoteEditor.java](file:///d:/develop/android%20studio/NotePad-main/app/src/main/java/com/example/android/notepad/NoteEditor.java) 的 `updateNote()` 已用 `System.currentTimeMillis()` 保存修改时间，存储值无需调整。
- [NotesList.java](file:///d:/develop/android%20studio/NotePad-main/app/src/main/java/com/example/android/notepad/NotesList.java) 的 `formatModificationTime()` 使用设备默认时区；模拟器实际为 GMT，因此比中国标准时间少 8 小时。
- 当前数据库 v4 没有分类字段；首页只有搜索条件，编辑器 Bottom Sheet 有置顶、分享、待办和恢复更改，没有分类入口。

## 三、已定交互与数据规则

- 时间统一使用 `TimeZone.getTimeZone("Asia/Shanghai")`，不跟随模拟器错误的 GMT 设置。
- 分类采用整数常量：`0=未分类`、`1=工作`、`2=生活`、`3=学习`、`4=其他`。
- 新笔记和迁移前的旧笔记默认“未分类”。
- 编辑器分类选项为“未分类、工作、生活、学习、其他”；首页筛选项额外包含“全部分类”，对应内存筛选值 `-1`，不写入数据库。
- 分类标签只在已分类笔记卡片上显示，“未分类”不额外占用卡片空间。
- 选择不同分类后立即保存；选择当前分类不重复保存，避免无意义刷新修改时间。
- 本轮不做自定义分类、分类重命名/删除、独立分类管理页面或提醒功能。

## 四、具体修改

### 1. 先补失败测试

修改 [NotePadActivityTest.java](file:///d:/develop/android%20studio/NotePad-main/app/src/androidTest/java/com/example/android/notepad/NotePadActivityTest.java) 和 [NotePadProviderTest.java](file:///d:/develop/android%20studio/NotePad-main/app/src/androidTest/java/com/example/android/notepad/NotePadProviderTest.java)，按 RED → GREEN 顺序逐项实施：

1. `testListItemFormatsTimeInAsiaShanghaiWhenDeviceIsGmt`：临时把默认时区设为 GMT，明确断言列表按上海时区输出，`finally` 恢复原时区；现有完整时间测试同步改为固定上海时区期望。
2. `testCategoryColumnDefaultsAndUpdates`：验证插入默认分类、更新分类及查询结果。
3. `testUpgradeFrom4PreservesNotesAndDefaultsCategory`：构造真实 v4 数据，升级后验证原字段不变且分类为未分类；既有旧版本迁移测试升级到 v5。
4. `testListItemShowsCategoryLabel`：验证已分类笔记显示正确标签，未分类笔记不显示标签。
5. `testCategoryFilterCombinesWithSearch` 与 `testAllCategoriesRestoresNotes`：验证分类和搜索的 AND 关系，以及切回全部后恢复结果。
6. `testEditorBottomSheetChangesCategoryAndSaves`：通过现有 Bottom Sheet 入口选择分类，验证 Provider 已保存且重新打开时选中状态正确。

每个测试必须先运行并确认因功能缺失而失败，再写最小生产代码使其通过。

### 2. 分类契约与数据库 v5

修改 [NotePad.java](file:///d:/develop/android%20studio/NotePad-main/app/src/main/java/com/example/android/notepad/NotePad.java)：

- 增加 `COLUMN_NAME_CATEGORY = "category"` 和上述分类整数常量。
- 保持现有默认排序不变。

修改 [NotePadProvider.java](file:///d:/develop/android%20studio/NotePad-main/app/src/main/java/com/example/android/notepad/NotePadProvider.java)：

- 数据库版本升为 5。
- 建表新增 `category INTEGER NOT NULL DEFAULT 0`。
- `onUpgrade()` 在 `oldVersion < 5` 时执行 `ALTER TABLE notes ADD COLUMN category INTEGER NOT NULL DEFAULT 0`。
- 查询 projection map 增加分类字段；插入时未提供分类则写入 `0`。
- 不改变 `created/modified` 的存储方式，也不让 Provider 普通更新自动重写修改时间。

### 3. 固定上海时区显示

修改 [NotesList.java](file:///d:/develop/android%20studio/NotePad-main/app/src/main/java/com/example/android/notepad/NotesList.java)：

- 在 `formatModificationTime()` 创建 `SimpleDateFormat` 后设置 `Asia/Shanghai` 时区。
- 保留秒级格式与设备 Locale，不修改数据库原值，不使用手工毫秒偏移。

### 4. 首页分类筛选与卡片标签

修改 [notes_list.xml](file:///d:/develop/android%20studio/NotePad-main/app/src/main/res/layout/notes_list.xml)：

- 在搜索区域和列表之间增加原生 `Spinner`，沿用现有主题、间距和圆角搜索区，不引入第三方组件。

修改 [NotesList.java](file:///d:/develop/android%20studio/NotePad-main/app/src/main/java/com/example/android/notepad/NotesList.java)：

- projection、adapter 映射和 `ViewBinder` 增加分类字段，将整数映射为资源中的中文名称。
- Spinner 使用原生 `simple_spinner_item` 和 `simple_spinner_dropdown_item`。
- 统一由 `refreshNotes()` 查询并 `changeCursor()`，同时正确关闭旧 Cursor。
- `queryNotes()` 组合条件：未选全部时添加 `category=?`；搜索非空时添加带括号的 `(title LIKE ? OR note LIKE ?)`；两组条件用 `AND` 连接，参数顺序与 SQL 一致。
- 搜索、分类切换和待办完成状态更新都走同一刷新路径，避免刷新后丢失当前分类条件。

修改 [noteslist_item.xml](file:///d:/develop/android%20studio/NotePad-main/app/src/main/res/layout/noteslist_item.xml)：

- 在时间行增加分类 `TextView`，复用现有文字颜色和间距；未分类时设为 `GONE`，并在 adapter 复用时显式重置可见性和文字。

### 5. 编辑器分类设置

修改 [editor_more_sheet.xml](file:///d:/develop/android%20studio/NotePad-main/app/src/main/res/layout/editor_more_sheet.xml) 和 [NoteEditor.java](file:///d:/develop/android%20studio/NotePad-main/app/src/main/java/com/example/android/notepad/NoteEditor.java)：

- 复用现有 Bottom Sheet 行结构增加“分类”入口，复用 `LineIconDrawable.NOTE`，不新增图标绘制代码。
- 点击后关闭 Sheet，由 NoteEditor 弹出现有原生 `AlertDialog` 单选列表，默认勾选当前分类。
- 加载笔记时读取 `mCategory`；`updateNote()` 同步保存分类，避免编辑正文或待办状态把分类重置。
- 选择不同分类后更新状态并调用现有 `saveCurrentNote()`，相同分类直接关闭对话框。

### 6. 文案资源

修改 [strings.xml](file:///d:/develop/android%20studio/NotePad-main/app/src/main/res/values/strings.xml)：

- 增加“笔记分类”“全部分类”及分类名称/筛选名称数组。
- 保持全中文文案；不新增无用布局、Activity、Drawable 或文档文件。

## 五、验证与验收

1. 每个新增测试先确认 RED，再执行对应实现并确认 GREEN。
2. 运行 Provider 与 Activity 全部 `androidTest`，重点回归时间、标题/正文搜索、中文输入、待办点击与完成、置顶、分享和自动保存。
3. 构建 debug APK；如 Android 17 模拟器拦截 targetSdk 23，沿用 `adb install -r --bypass-low-target-sdk-block` 安装，不修改 SDK 配置。
4. 实机/模拟器验收：
   - 在当前 Windows 为 UTC+8、模拟器为 GMT 的环境中，新保存笔记列表时间仍与 Windows 当前时间一致到秒。
   - 新建笔记默认未分类；设置工作/生活/学习/其他后卡片标签正确，重新进入编辑器选择状态保留。
   - 首页分类筛选、关键词搜索及二者组合结果正确；切回全部分类恢复列表。
   - 升级安装后旧笔记及其时间、待办、完成、置顶数据均保留。
5. 清理验收产生的测试笔记，不创建 Git 提交。
