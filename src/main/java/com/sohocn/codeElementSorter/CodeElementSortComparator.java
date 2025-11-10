package com.sohocn.codeElementSorter;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;

import java.util.Comparator;

public class CodeElementSortComparator implements Comparator<PsiMember> {
    
    @Override
    public int compare(PsiMember member1, PsiMember member2) {
        // First priority: Element type (fields before methods)
        int typeComparison = compareByType(member1, member2);
        if (typeComparison != 0) {
            return typeComparison;
        }

        // Second priority: Static modifier (static before non-static)
        int staticComparison = compareByStatic(member1, member2);
        if (staticComparison != 0) {
            return staticComparison;
        }
        
        // Third priority: Visibility (public before package-private before protected before private)
        int visibilityComparison = compareByVisibility(member1, member2);
        if (visibilityComparison != 0) {
            return visibilityComparison;
        }
        
        // Fourth priority: Name in alphabetical order (case-insensitive)
        return compareByName(member1, member2);
    }
    
    private int compareByType(PsiMember member1, PsiMember member2) {
        boolean isField1 = member1 instanceof PsiField;
        boolean isField2 = member2 instanceof PsiField;
        
        // Fields come before methods
        if (isField1 && !isField2) {
            return -1;
        } else if (!isField1 && isField2) {
            return 1;
        } else {
            return 0; // Same type
        }
    }

    private int compareByStatic(PsiMember member1, PsiMember member2) {
        boolean isStatic1 = member1.hasModifierProperty(PsiModifier.STATIC);
        boolean isStatic2 = member2.hasModifierProperty(PsiModifier.STATIC);

        if (isStatic1 && !isStatic2) {
            return -1; // Static members first
        } else if (!isStatic1 && isStatic2) {
            return 1;
        } else {
            return 0; // Both are static or both are not
        }
    }
    
    private int compareByVisibility(PsiMember member1, PsiMember member2) {
        // Get visibility priorities for both members
        int visibility1 = getVisibilityPriority(member1);
        int visibility2 = getVisibilityPriority(member2);
        
        return Integer.compare(visibility1, visibility2);
    }
    
    private int getVisibilityPriority(PsiMember member) {
        if (member.getModifierList() == null) {
            return 1; // If no modifier list, assume package-private (default)
        }
        
        if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
            return 0; // Public first
        } else if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
            return 2; // Protected third
        } else if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
            return 3; // Private last
        } else {
            return 1; // Package-private (default) second
        }
    }
    
    private int compareByName(PsiMember member1, PsiMember member2) {
        String name1 = member1.getName();
        String name2 = member2.getName();
        
        if (name1 == null) name1 = "";
        if (name2 == null) name2 = "";
        
        // Case-insensitive alphabetical order
        return name1.toLowerCase().compareTo(name2.toLowerCase());
    }
}