package com.sohocn.codeElementSorter;

import java.util.Comparator;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;

/**
 * The type Code element sort comparator.
 *
 * @author longjianghu
 */
public class CodeElementSortComparator implements Comparator<PsiMember> {
    @Override
    public int compare(PsiMember member1, PsiMember member2) {
        int typeComparison = this.compareByType(member1, member2);

        if (typeComparison != 0) {
            return typeComparison;
        }

        if (member1 instanceof PsiField && member2 instanceof PsiField) {
            int listComparison = this.compareByListType((PsiField) member1, (PsiField) member2);

            if (listComparison != 0) {
                return listComparison;
            }
        }

        int staticComparison = this.compareByStatic(member1, member2);

        if (staticComparison != 0) {
            return staticComparison;
        }

        int visibilityComparison = this.compareByVisibility(member1, member2);

        if (visibilityComparison != 0) {
            return visibilityComparison;
        }

        return this.compareByName(member1, member2);
    }

    private int compareByListType(PsiField field1, PsiField field2) {
        boolean isField1List = isListField(field1);
        boolean isField2List = isListField(field2);

        if (isField1List && !isField2List) {
            return 1;
        } else if (!isField1List && isField2List) {
            return -1;
        } else {
            return 0;
        }
    }

    private int compareByName(PsiMember member1, PsiMember member2) {
        String name1 = member1.getName();
        String name2 = member2.getName();

        name1 = (name1 != null) ? name1 : "";
        name2 = (name2 != null) ? name2 : "";

        return name1.toLowerCase().compareTo(name2.toLowerCase());
    }

    private int compareByStatic(PsiMember member1, PsiMember member2) {
        boolean isStatic1 = member1.hasModifierProperty(PsiModifier.STATIC);
        boolean isStatic2 = member2.hasModifierProperty(PsiModifier.STATIC);

        if (isStatic1 && !isStatic2) {
            return -1;
        } else if (!isStatic1 && isStatic2) {
            return 1;
        } else {
            return 0;
        }
    }

    private int compareByType(PsiMember member1, PsiMember member2) {
        boolean isField1 = member1 instanceof PsiField;
        boolean isField2 = member2 instanceof PsiField;

        if (isField1 && !isField2) {
            return -1;
        } else if (!isField1 && isField2) {
            return 1;
        } else {
            return 0;
        }
    }

    private int compareByVisibility(PsiMember member1, PsiMember member2) {
        int visibility1 = this.getVisibilityPriority(member1);
        int visibility2 = this.getVisibilityPriority(member2);

        return Integer.compare(visibility1, visibility2);
    }

    private int getVisibilityPriority(PsiMember member) {
        if (member.getModifierList() == null) {
            return 1;
        }

        if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
            return 0;
        } else if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
            return 2;
        } else if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
            return 3;
        } else {
            return 1;
        }
    }

    private boolean isListField(PsiField field) {
        if (field == null) {
            return false;
        } else {
            field.getType();
        }

        PsiType fieldType = field.getType();
        String fieldTypeName = fieldType.getCanonicalText();

        return fieldTypeName.startsWith("java.util.List") ||
                fieldTypeName.startsWith("java.util.ArrayList") ||
                fieldTypeName.startsWith("java.util.LinkedList") ||
                fieldTypeName.startsWith("java.util.Vector") ||
                fieldTypeName.startsWith("java.util.Stack") ||
                fieldTypeName.contains(".List");
    }
}