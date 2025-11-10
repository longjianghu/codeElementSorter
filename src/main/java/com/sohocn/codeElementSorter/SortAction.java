package com.sohocn.codeElementSorter;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiParserFacade;
import com.intellij.openapi.editor.SelectionModel;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class SortAction extends AnAction {
    
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiFile psiFile = e.getData(PlatformDataKeys.PSI_FILE);

        if (project == null || editor == null || psiFile == null) {
            Messages.showErrorDialog("No active project, editor, or file found", "Error");
            return;
        }

        if (!(psiFile instanceof PsiJavaFile)) {
            Messages.showErrorDialog("File is not a Java file", "Error");
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();

        // 根据用户是否选择代码来决定执行全文件排序还是选中部分排序
        if (selectionModel.hasSelection()) {
            // 用户选择了代码，执行选中部分排序
            sortSelectedMembers(project, editor, psiFile);
        } else {
            // 用户未选择代码，执行全文件排序
            sortAllMembers(project, editor, psiFile);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Enable the action only when in a Java file
        PsiFile psiFile = e.getData(PlatformDataKeys.PSI_FILE);
        boolean isJavaFile = psiFile instanceof PsiJavaFile;
        e.getPresentation().setEnabledAndVisible(isJavaFile);
    }

    // 排序整个文件
    public void sortAllMembers(@NotNull Project project, Editor editor, PsiFile psiFile) {
        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        PsiClass[] classes = javaFile.getClasses();

        if (classes.length == 0) {
            Messages.showInfoMessage("No classes found in file", "Info");
            return;
        }

        // Process the first class in the file
        PsiClass psiClass = classes[0];
        sortClassMembers(project, editor, psiClass, null);
    }
    
    // 排 sor 选中的成员
    public void sortSelectedMembers(@NotNull Project project, Editor editor, PsiFile psiFile) {
        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        PsiClass[] classes = javaFile.getClasses();

        if (classes.length == 0) {
            Messages.showInfoMessage("No classes found in file", "Info");
            return;
        }

        PsiClass psiClass = classes[0]; // Assuming first class for simplicity
        if (psiClass == null) {
            Messages.showInfoMessage("No class found in file", "Info");
            return;
        }
        
        SelectionModel selectionModel = editor.getSelectionModel();
        int startOffset = selectionModel.getSelectionStart();
        int endOffset = selectionModel.getSelectionEnd();
        
        // Find members within the selected range
        List<PsiMember> membersToSort = new ArrayList<>();
        for (PsiElement element : psiClass.getChildren()) {
            if (element instanceof PsiMember &&
                (element instanceof PsiField || element instanceof PsiMethod)) {
                
                int memberStart = element.getTextRange().getStartOffset();
                int memberEnd = element.getTextRange().getEndOffset();
                
                // Check if member is within selection range
                if (memberStart >= startOffset && memberEnd <= endOffset) {
                    membersToSort.add((PsiMember) element);
                }
            }
        }
        
        if (membersToSort.isEmpty()) {
            Messages.showInfoMessage("No sortable elements found in selection", "Info");
            return;
        }
        
        // Sort only the selected members
        sortClassMembers(project, editor, psiClass, membersToSort);
    }

    private PsiClass getPsiClassAtOffset(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        return PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }

    public void sortClassMembers(@NotNull Project project, Editor editor, PsiClass psiClass, List<PsiMember> membersToSort) {
        List<PsiMember> members;

        if (membersToSort == null) {
            // Get all members in the class
            members = new ArrayList<>();
            for (PsiElement element : psiClass.getChildren()) {
                if (element instanceof PsiMember &&
                    (element instanceof PsiField || element instanceof PsiMethod)) {
                    members.add((PsiMember) element);
                }
            }
        } else {
            members = membersToSort;
        }

        // Filter out only fields and methods
        List<PsiMember> sortableMembers = members.stream()
                .filter(member -> member instanceof PsiField || member instanceof PsiMethod)
                .collect(Collectors.toList());

        if (sortableMembers.isEmpty()) {
            Messages.showInfoMessage("No sortable elements found", "Info");
            return;
        }

        // Apply changes to the file
        WriteCommandAction.runWriteCommandAction(project, () -> {
            if (membersToSort == null) {
                // Perform sorting and reorganization
                performFullSorting(project, psiClass, sortableMembers);
            } else {
                // For selected members - replace only the selected ones
                performSelectedSorting(project, psiClass, membersToSort);
            }
        });

        // Show appropriate message
        if (membersToSort != null) {
            Messages.showInfoMessage("Sorted " + sortableMembers.size() + " selected elements", "Success");
        } else {
            // Count fields and methods
            long fieldCount = sortableMembers.stream().filter(m -> m instanceof PsiField).count();
            long methodCount = sortableMembers.stream().filter(m -> m instanceof PsiMethod).count();
            Messages.showInfoMessage("Sorted " + sortableMembers.size() + " elements: " +
                    fieldCount + " fields, " + methodCount + " methods", "Success");
        }
    }
    
    /**
     * Perform full class sorting according to the README rules
     */
    private void performFullSorting(@NotNull Project project, PsiClass psiClass, List<PsiMember> sortableMembers) {
        // Group fields and methods separately according to README rules
        
        // Group 1: Static fields
        List<PsiField> staticFields = sortableMembers.stream()
            .filter(m -> m instanceof PsiField)
            .map(m -> (PsiField) m)
            .filter(field -> field.hasModifierProperty(PsiModifier.STATIC))
            .collect(Collectors.toList());

        // Group 2: Regular instance variables (unannotated)
        List<PsiField> regularInstanceFields = sortableMembers.stream()
            .filter(m -> m instanceof PsiField)
            .map(m -> (PsiField) m)
            .filter(field -> !field.hasModifierProperty(PsiModifier.STATIC) && field.getAnnotations().length == 0)
            .collect(Collectors.toList());
        
        // Group 3: Annotated instance variables
        List<PsiField> annotatedInstanceFields = sortableMembers.stream()
            .filter(m -> m instanceof PsiField)
            .map(m -> (PsiField) m)
            .filter(field -> !field.hasModifierProperty(PsiModifier.STATIC) && field.getAnnotations().length > 0)
            .collect(Collectors.toList());
        
        // Group 4: Methods
        List<PsiMethod> methods = sortableMembers.stream()
            .filter(m -> m instanceof PsiMethod)
            .map(m -> (PsiMethod) m)
            .collect(Collectors.toList());
        
        // Sort each group using the comparator
        staticFields.sort(new CodeElementSortComparator());
        regularInstanceFields.sort(new CodeElementSortComparator());
        annotatedInstanceFields.sort(new CodeElementSortComparator());
        methods.sort(new CodeElementSortComparator());

        // Create copies of the sorted members - this must be done before deletion
        List<PsiElement> staticFieldCopies = createElementCopiesWithRelatedElements(staticFields);
        List<PsiElement> regularInstanceFieldCopies = createElementCopiesWithRelatedElements(regularInstanceFields);
        List<PsiElement> annotatedInstanceFieldCopies = createElementCopiesWithRelatedElements(annotatedInstanceFields);
        List<PsiElement> methodCopies = createElementCopiesWithRelatedElements(methods);

        // Delete all original sortable members (this will remove them and their preceding comments)
        deleteMembersWithComments(psiClass, sortableMembers);

        // Add members back in the correct order with appropriate spacing
        addMembersWithSpacing(project, psiClass, staticFieldCopies, regularInstanceFieldCopies, annotatedInstanceFieldCopies, methodCopies);
    }
    
    /**
     * Create copies of a list of fields
     */
    private List<PsiField> createFieldCopies(List<PsiField> fields) {
        List<PsiField> copies = new ArrayList<>();
        for (PsiField field : fields) {
            copies.add((PsiField) field.copy());
        }
        return copies;
    }
    
    /**
     * Create copies of a list of methods
     */
    private List<PsiMethod> createMethodCopies(List<PsiMethod> methods) {
        List<PsiMethod> copies = new ArrayList<>();
        for (PsiMethod method : methods) {
            copies.add((PsiMethod) method.copy());
        }
        return copies;
    }
    
    /**
     * Create copies of list of PSI elements (fields/methods with their comments)
     */
    private List<PsiElement> createElementCopiesWithComments(List<? extends PsiMember> members) {
        List<PsiElement> copies = new ArrayList<>();
        for (PsiMember member : members) {
            // Get related comments and annotations that should move with the member
            List<PsiElement> relatedElements = getRelatedElements(member);
            for (PsiElement element : relatedElements) {
                copies.add((PsiElement) element.copy());
            }
            // Add the member itself
            copies.add((PsiElement) member.copy());
        }
        return copies;
    }
    
    /**
     * Delete a list of members from the class
     */
    private void deleteMembers(PsiClass psiClass, List<PsiMember> membersToDelete) {
        // Delete from last to first to maintain text offsets
        membersToDelete.sort((a, b) -> b.getTextRange().getStartOffset() - a.getTextRange().getStartOffset());
        for (PsiMember member : membersToDelete) {
            member.delete();
        }
    }
    
    /**
     * Delete a list of members from the class
     */
    private void deleteMembersWithComments(PsiClass psiClass, List<PsiMember> membersToDelete) {
        // Delete from last to first to maintain text offsets
        membersToDelete.sort((a, b) -> b.getTextRange().getStartOffset() - a.getTextRange().getStartOffset());
        for (PsiMember member : membersToDelete) {
            // Get related comments first (before deleting the member)
            List<PsiElement> relatedElements = getRelatedElements(member);
            // Delete related elements first
            for (PsiElement element : relatedElements) {
                if (element.isValid()) {
                    element.delete();
                }
            }
            // Then delete the member itself
            if (member.isValid()) {
                member.delete();
            }
        }
    }
    
    /**
     * Perform sorting for selected members only
     */
    private void performSelectedSorting(@NotNull Project project, PsiClass psiClass, List<PsiMember> membersToSort) {
        // Filter out only fields and methods from the selected members
        List<PsiMember> sortableMembers = membersToSort.stream()
                .filter(member -> member instanceof PsiField || member instanceof PsiMethod)
                .collect(Collectors.toList());

        if (sortableMembers.isEmpty()) {
            return;
        }

        // Sort using the comparator
        sortableMembers.sort(new CodeElementSortComparator());

        // Delete all original selected sortable members and their related comments
        deleteMembersWithComments(psiClass, sortableMembers);

        // Create copies of elements (with their related comments/annotations)
        List<PsiElement> copies = createElementCopiesWithComments(sortableMembers);

        // Add members back in sorted order with their comments
        for (PsiElement copy : copies) {
            psiClass.add(copy);
        }
    }
    
    /**
     * Check if the element has a Javadoc comment
     */
    private boolean hasJavadocComment(PsiElement element) {
        if (element instanceof PsiDocCommentOwner) {
            return ((PsiDocCommentOwner) element).getDocComment() != null;
        }
        return false;
    }
    
    /**
     * Check if the field has annotations
     */
    private boolean hasAnnotations(PsiField field) {
        return field.getAnnotations().length > 0;
    }
    
    /**
     * Create copies of list of PSI elements with their related elements
     */
    private List<PsiElement> createElementCopiesWithRelatedElements(List<? extends PsiMember> members) {
        List<PsiElement> allCopies = new ArrayList<>();
        for (PsiMember member : members) {
            // Get related comments that should move with the member
            List<PsiElement> relatedElements = getRelatedElements(member);
            for (PsiElement element : relatedElements) {
                if (element.isValid()) {
                    allCopies.add((PsiElement) element.copy());
                }
            }
            // Add the member itself if it's valid
            if (member.isValid()) {
                allCopies.add((PsiElement) member.copy());
            }
        }
        return allCopies;
    }
    
    /**
     * Get all elements that should be moved with the member (comments and whitespace)
     */
    private List<PsiElement> getRelatedElements(PsiMember member) {
        List<PsiElement> relatedElements = new ArrayList<>();
        
        // Look for preceding comments and whitespace
        PsiElement prevSibling = member.getPrevSibling();
        while (prevSibling != null) {
            if (prevSibling instanceof PsiComment || 
                prevSibling instanceof PsiWhiteSpace) {
                // Only include comments and whitespace that come before the member
                relatedElements.add(0, prevSibling); // Add to the beginning to maintain order
                prevSibling = prevSibling.getPrevSibling();
            } else {
                // Stop if we encounter something that's not a comment or whitespace
                break;
            }
        }
        
        return relatedElements;
    }
    
    /**
     * Add all members back to the class with appropriate spacing
     */
    private void addMembersWithSpacing(@NotNull Project project, PsiClass psiClass,
                                       List<PsiElement> staticFieldCopies,
                                       List<PsiElement> regularInstanceFieldCopies,
                                       List<PsiElement> annotatedInstanceFieldCopies,
                                       List<PsiElement> methodCopies) {

        boolean hasContent = false;

        // Add static fields
        if (!staticFieldCopies.isEmpty()) {
            for (PsiElement element : staticFieldCopies) {
                psiClass.add(element);
                if (element instanceof PsiField && (hasJavadocComment(element) || hasAnnotations((PsiField) element))) {
                    addBlankLine(project, psiClass);
                }
            }
            hasContent = true;
        }

        // Add regular instance fields
        if (!regularInstanceFieldCopies.isEmpty()) {
            if (hasContent) {
                addBlankLine(project, psiClass);
            }
            for (PsiElement element : regularInstanceFieldCopies) {
                psiClass.add(element);
                if (element instanceof PsiField && hasJavadocComment(element)) {
                    addBlankLine(project, psiClass);
                }
            }
            hasContent = true;
        }

        // Add annotated instance fields
        if (!annotatedInstanceFieldCopies.isEmpty()) {
            if (hasContent) {
                addBlankLine(project, psiClass);
            }
            for (PsiElement element : annotatedInstanceFieldCopies) {
                psiClass.add(element);
                if (element instanceof PsiField) {
                    addBlankLine(project, psiClass);
                }
            }
            hasContent = true;
        }

        // Add methods
        if (!methodCopies.isEmpty()) {
            if (hasContent) {
                addBlankLine(project, psiClass);
            }
            for (PsiElement element : methodCopies) {
                psiClass.add(element);
                if (element instanceof PsiMethod && hasJavadocComment(element)) {
                    addBlankLine(project, psiClass);
                }
            }
        }
    }

    private void addBlankLine(@NotNull Project project, PsiClass psiClass) {
        try {
            PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n");
            psiClass.add(whiteSpace);
        } catch (Exception e) {
            // Fails silently
        }
    }
}
