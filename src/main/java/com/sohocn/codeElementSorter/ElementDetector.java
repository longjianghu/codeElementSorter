package com.sohocn.codeElementSorter;

import com.intellij.psi.*;
import java.util.ArrayList;
import java.util.List;

public class ElementDetector {
    
    /**
     * 从PsiClass中提取所有可排序的元素（字段和方法）
     * @param psiClass PsiClass对象
     * @return 包含所有字段和方法的列表
     */
    public static List<PsiElement> extractSortableElements(PsiClass psiClass) {
        List<PsiElement> elements = new ArrayList<>();
        
        // 添加所有字段
        PsiField[] fields = psiClass.getFields();
        for (PsiField field : fields) {
            // 排除内部类和枚举
            if (!isInnerClassOrEnum(field)) {
                elements.add(field);
            }
        }
        
        // 添加所有方法（排除构造函数）
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod method : methods) {
            // 排除构造函数
            if (!method.isConstructor()) {
                elements.add(method);
            }
        }
        
        return elements;
    }
    
    /**
     * 检查元素是否为内部类或枚举
     * @param element PsiElement对象
     * @return 是否为内部类或枚举
     */
    public static boolean isInnerClassOrEnum(PsiElement element) {
        // 检查是否为类成员（包括内部类）
        if (element instanceof PsiClass) {
            // 这是内部类
            return true;
        }
        
        // 检查字段的类型是否为枚举
        if (element instanceof PsiField) {
            PsiType fieldType = ((PsiField) element).getType();
            if (fieldType instanceof PsiClassType) {
                PsiClass fieldClass = ((PsiClassType) fieldType).resolve();
                if (fieldClass != null && fieldClass.isEnum()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查方法是否为内部类方法（辅助方法）
     * @param method PsiMethod对象
     * @return 是否为内部类方法
     */
    public static boolean isInnerClassMethod(PsiMethod method) {
        // 对于基本实现，我们不特别排除任何方法
        // 在完整实现中，这里可以检查方法是否属于内部类
        return false;
    }
    
    /**
     * 专门用于从选择区域提取元素的方法
     */
    public static List<PsiElement> extractElementsFromSelection(PsiFile psiFile, int startOffset, int endOffset) {
        List<PsiElement> elements = new ArrayList<>();
        
        // 获取选择范围内的元素
        PsiElement startElement = psiFile.findElementAt(startOffset);
        PsiElement endElement = psiFile.findElementAt(endOffset > 0 ? endOffset - 1 : endOffset);
        
        if (startElement == null || endElement == null) {
            return elements;
        }
        
        // 遍历范围内的所有可能元素
        PsiElement current = startElement;
        while (current != null && current.getTextOffset() < endOffset) {
            if (current instanceof PsiField || current instanceof PsiMethod) {
                // 确保元素完全在选择范围内
                int elementStart = current.getTextOffset();
                int elementEnd = elementStart + current.getTextLength();
                
                if (elementStart >= startOffset && elementEnd <= endOffset) {
                    elements.add(current);
                }
            }
            
            // 移动到下一个元素
            if (current.getNextSibling() != null) {
                current = current.getNextSibling();
            } else {
                // 如果没有下一个兄弟节点，向上查找
                current = findNextElement(current.getParent(), endOffset);
                break; // 避免无限循环，需要更复杂的逻辑
            }
        }
        
        return elements;
    }
    
    /**
     * 辅助方法：查找下一个元素
     */
    private static PsiElement findNextElement(PsiElement parent, int endOffset) {
        if (parent == null) return null;
        
        PsiElement next = parent.getNextSibling();
        if (next != null) {
            return next;
        } else {
            return findNextElement(parent.getParent(), endOffset);
        }
    }
}

