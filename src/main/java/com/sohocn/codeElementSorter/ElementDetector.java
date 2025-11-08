package com.sohocn.codeElementSorter;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;

import java.util.ArrayList;
import java.util.List;

public class ElementDetector {
    
    /**
     * Detects all sortable elements (fields and methods) within the given PSI elements
     * @param elements The PSI elements to scan
     * @return List of sortable PsiMembers (fields and methods)
     */
    public static List<PsiMember> detectSortableElements(Iterable<PsiElement> elements) {
        List<PsiMember> sortableElements = new ArrayList<>();
        
        for (PsiElement element : elements) {
            if (element instanceof PsiMember) {
                PsiMember member = (PsiMember) element;
                // Only include fields and methods
                if (member instanceof PsiField || member instanceof PsiMethod) {
                    sortableElements.add(member);
                }
            }
        }
        
        return sortableElements;
    }
    
    /**
     * Checks if an element is a sortable member (field or method)
     * @param element The element to check
     * @return true if element is a field or method, false otherwise
     */
    public static boolean isSortableElement(PsiElement element) {
        return element instanceof PsiField || element instanceof PsiMethod;
    }
}