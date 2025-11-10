# Code Element Sorter - IntelliJ IDEA 插件功能说明书

## 1. 产品概述

### 1.1 插件名称
**Code Element Sorter**

### 1.2 插件类型
代码结构增强工具

### 1.3 核心价值
智能排序当前页面的变量和方法，按照统一的排序规则提升代码整洁度和可读性。

## 2. 核心功能特性

### 2.1 智能排序模式

#### 🔄 全文件排序模式
- **触发条件**：在任何Java文件中点击排序菜单项且未选择任何代码
- **排序范围**：对整个当前打开的Java文件进行排序
- **适用场景**：整体代码重构和规范化

#### 🔄 选中部分排序模式
- **触发条件**：在任何Java文件中选中部分代码后点击排序菜单项
- **排序范围**：仅对选中的Java成员进行排序
- **适用场景**：局部代码整理和优化

### 2.2 智能排序规则

#### 排序优先级层次
```
第一优先级：元素类型分组（普通变量 → 注解变量 → 方法）
    ↓
第二优先级：可见性（公共 → 私有）
    ↓  
第三优先级：名称字母顺序（深度比较）
```

#### 详细排序顺序
1. **普通公共变量** (`public` fields)
2. **普通包私有变量** (无修饰符 fields)
3. **普通受保护变量** (`protected` fields)
4. **普通私有变量** (`private` fields)
5. **注解公共变量** (`public` fields with annotations)
6. **注解包私有变量** (无修饰符 fields with annotations)
7. **注解受保护变量** (`protected` fields with annotations)
8. **注解私有变量** (`private` fields with annotations)
9. **公共方法** (`public` methods)
10. **包私有方法** (无修饰符 methods)
11. **受保护方法** (`protected` methods)
12. **私有方法** (`private` methods)

### 2.3 深度字母排序
- **A-Z字典序**：按元素名称进行字母顺序排序
- **深度比较**：如果首字母相同，比较第二个字母，依此类推
- **不区分大小写**：统一转为小写进行比较

## 3. 用户交互设计

### 3.1 智能右键菜单
```
Editor Popup Menu
  ↳ 🔄 Sort Members A-Z                    ← 智能排序（根据选择自动决定）
```

#### 自动模式切换
- **未选择代码时**：自动执行全文件排序
- **选择部分代码时**：自动执行选中部分排序

### 3.2 操作反馈
- **全文件排序成功**：`"Sorted 8 elements: 3 fields, 5 methods"`
- **选中部分排序成功**：`"Sorted 3 selected elements"`
- **无元素警告**：`"No sortable elements found"`

## 4. 排序示例

### 4.1 排序前代码
```java
public class UserService {
    private String logPrefix;
    public static final int MAX_RETRY = 3;
    private UserRepository repository;
    
    @Resource
    private UserService userService;
    
    @Autowired
    private UserRepository userRepository;
    
    private void validateInput() { }
    public void createUser(User user) { }
    public void updateUser(Long id) { }
    private Logger logger;
    public void deleteUser(Long id) { }
}
```

### 4.2 排序后代码
```java
public class UserService {
    // 普通变量区域（按可见性→名称深度排序）
    public static final int MAX_RETRY = 3;
    private Logger logger;
    private String logPrefix;
    private UserRepository repository;
    
    // 注解变量区域（按可见性→名称深度排序）
    @Autowired
    private UserRepository userRepository;
    
    @Resource
    private UserService userService;
    
    // 方法区域（按可见性→名称深度排序）
    public void createUser(User user) { }
    public void deleteUser(Long id) { }
    public void updateUser(Long id) { }
    private void validateInput() { }
}
```

## 5. 技术实现架构

### 5.1 核心组件设计

#### 元素识别器 (ElementDetector)
```java
// 负责识别和分类代码元素
- 识别成员变量 (PsiField)
- 识别方法定义 (PsiMethod)
- 检测元素可见性
- 验证选择区域的完整性
```

#### 排序策略器 (SortingStrategy) 
```java
// 实现分组排序规则
- 普通变量分组 (按可见性→名称深度排序)
- 注解变量分组 (按可见性→名称深度排序)
- 方法分组 (按可见性→名称深度排序)
- 组间之间添加空行
- 第一个元素前面不要添加空行
- 如果变量带注释,也需要在变量后面添加空行
- 如果变量使用/**这样的多行注释或带有注解,在变量后面添加一个空行
```

#### 模式检测器 (ModeDetector)
```java
// 智能判断操作模式
- 检测选择状态
- 验证选择区域有效性
- 返回适当的排序模式
```

### 5.2 排序算法流程
```
1. 解析当前文件或选择区域
2. 识别所有可排序元素
3. 应用分组排序规则：
   - 普通变量分组排序（按可见性→名称深度排序）
   - 注解变量分组排序（按可见性→名称深度排序）
   - 方法分组排序（按可见性→名称深度排序）
   - 组间添加分隔空行
4. 重新生成排序后的代码
5. 应用代码变更
```

## 6. 边界情况处理

### 6.1 特殊元素处理
- **构造方法**：保持原位置不变或按方法规则参与排序
- **静态成员**：与非静态成员混合时仍按可见性优先级排序
- **内部类/枚举**：不参与排序，保持原有位置
- **注解元素**：保持附着元素的关联性

### 6.2 错误处理机制
- **语法错误**：在包含语法错误的文件中禁用排序功能
- **选择不完整**：自动回退到全页面排序模式
- **排序冲突**：确保稳定排序，相同元素保持相对顺序

## 7. 兼容性要求

### 7.1 开发环境
- **IntelliJ IDEA**：2022.3+ 版本
- **Java版本**：JDK 17+
- **构建工具**：Gradle 7.0+

### 7.2 支持的语言特性
- ✅ Java 8+ 所有语法特性
- ✅ 注解处理
- ✅ 泛型类型
- ✅ Lambda表达式
- ✅ 记录类 (Record Classes)

## 8. 性能指标

### 8.1 响应时间要求
- **小文件** (< 100行)：< 100ms
- **中等文件** (100-500行)：< 500ms  
- **大文件** (> 500行)：< 1000ms

### 8.2 内存使用
- 峰值内存使用 < 50MB
- 无内存泄漏风险

## 9. 扩展规划

### 9.1 短期增强 (v1.1)
- [ ] 自定义排序规则配置
- [ ] 静态成员优先选项
- [ ] 构造方法排序控制

### 9.2 中期规划 (v1.2)  
- [ ] 支持Kotlin语言
- [ ] 项目级批量排序
- [ ] 排序规则预设模板

### 9.3 长期愿景 (v2.0)
- [ ] 多语言支持 (Python, JavaScript等)
- [ ] 智能分组排序 (按功能、业务逻辑等)
- [ ] 团队规则共享和同步

## 10. 用户体验保障

### 10.1 操作安全
- ✅ 完整的撤销/重做支持 (Ctrl+Z / Ctrl+Shift+Z)
- ✅ 排序前代码备份
- ✅ 错误恢复机制

### 10.2 视觉反馈
- ✅ 进度指示器（大文件操作时）
- ✅ 成功/失败状态提示
- ✅ 元素变更高亮显示

### 10.3 无障碍访问
- ✅ 键盘快捷键支持
- ✅ 屏幕阅读器兼容
- ✅ 高对比度主题适配

---

**总结**：Code Element Sorter 插件通过智能的双模式排序机制，为开发者提供了一键整理代码结构的强大工具，显著提升代码的可读性和维护性，是团队协作和代码规范化的理想助手。

