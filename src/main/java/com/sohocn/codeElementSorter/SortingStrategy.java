package com.sohocn.codeElementSorter;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;

import java.util.Comparator;

public class SortingStrategy implements Comparator<PsiElement> {
    
    @Override
    public int compare(PsiElement e1, PsiElement e2) {
        // 第一优先级：元素类型（变量 → 方法）
        int typePriority1 = getTypePriority(e1);
        int typePriority2 = getTypePriority(e2);
        
        if (typePriority1 != typePriority2) {
            return Integer.compare(typePriority1, typePriority2);
        }
        
        // 第二优先级：可见性（公共 → 私有）
        int visibilityPriority1 = getVisibilityPriority(e1);
        int visibilityPriority2 = getVisibilityPriority(e2);
        
        if (visibilityPriority1 != visibilityPriority2) {
            return Integer.compare(visibilityPriority1, visibilityPriority2);
        }
        
        // 第三优先级：名称字母顺序（深度比较）
        String name1 = getElementName(e1);
        String name2 = getElementName(e2);
        
        return name1.compareToIgnoreCase(name2);
    }
    
    /**
     * 获取元素类型优先级（字段优先于方法）
     * @param element PsiElement对象
     * @return 类型优先级（0=字段，1=方法）
     */
    private int getTypePriority(PsiElement element) {
        if (element instanceof PsiField) {
            return 0; // 字段优先
        } else if (element instanceof PsiMethod) {
            return 1; // 方法次之
        }
        return 2; // 其他元素最后
    }
    
    /**
     * 获取可见性优先级
     * @param element PsiElement对象
     * @return 可见性优先级（0=public, 1=protected, 2=package-private, 3=private）
     */
    private int getVisibilityPriority(PsiElement element) {
        PsiModifierList modifierList = null;
        
        if (element instanceof PsiField) {
            modifierList = ((PsiField) element).getModifierList();
        } else if (element instanceof PsiMethod) {
            modifierList = ((PsiMethod) element).getModifierList();
        }
        
        if (modifierList == null) {
            return 2; // 默认包私有
        }
        
        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            return 0;
        } else if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
            return 1;
        } else if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
            return 3;
        } else {
            return 2; // 包私有
        }
    }
    
    /**
     * 获取元素名称
     * @param element PsiElement对象
     * @return 元素名称
     */
    private String getElementName(PsiElement element) {
        if (element instanceof PsiField) {
            return ((PsiField) element).getName();
        } else if (element instanceof PsiMethod) {
            return ((PsiMethod) element).getName();
        }
        return element.toString();
    }
}
