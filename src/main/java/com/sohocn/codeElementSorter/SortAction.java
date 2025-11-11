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
        performFullSortingWithDepth(project, psiClass, sortableMembers, 0);
    }
    
    private void performFullSortingWithDepth(@NotNull Project project, PsiClass psiClass, List<PsiMember> sortableMembers, int depth) {
        // If we've reached the max depth (3), don't process deeper
        if (depth >= 3) {
            return;
        }
        
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
        
        // Separate inner classes from the current class
        List<PsiClass> innerClasses = new ArrayList<>();
        for (PsiElement element : psiClass.getChildren()) {
            if (element instanceof PsiClass && element != psiClass) {  // It's an inner class
                innerClasses.add((PsiClass) element);
                // Recursively sort the inner class if we haven't reached max depth
                if (depth < 2) {  // depth < 2 because current depth is for outer class, we want inner class to be at depth+1
                    List<PsiMember> innerSortableMembers = new ArrayList<>();
                    for (PsiElement innerElement : ((PsiClass) element).getChildren()) {
                        if (innerElement instanceof PsiMember &&
                                (innerElement instanceof PsiField || innerElement instanceof PsiMethod)) {
                            innerSortableMembers.add((PsiMember) innerElement);
                        }
                    }
                    performFullSortingWithDepth(project, (PsiClass) element, innerSortableMembers, depth + 1);
                }
            }
        }
        
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
        
        // Create copies of inner classes
        List<PsiElement> innerClassCopies = new ArrayList<>();
        for (PsiClass innerClass : innerClasses) {
            innerClassCopies.add((PsiElement) innerClass.copy());
        }

        // Delete all original sortable members and inner classes (this will remove them and their preceding comments)
        List<PsiMember> allMembersToDelete = new ArrayList<>(sortableMembers);
        for (PsiClass innerClass : innerClasses) {
            allMembersToDelete.add(innerClass);
        }
        deleteMembersWithComments(psiClass, allMembersToDelete);

        // Add members back in the correct order with appropriate spacing (inner classes last)
        addMembersWithSpacing(project, psiClass, staticFieldCopies, regularInstanceFieldCopies, annotatedInstanceFieldCopies, methodCopies, innerClassCopies);
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
        /**
     * Add all members back to the class with appropriate spacing
     */
        /**
     * Add all members back to the class with appropriate spacing
     */
        /**
     * Add all members back to the class with appropriate spacing
     */
        /**
     * Add all members back to the class with appropriate spacing
     */
        /**
     * Add all members back to the class with appropriate spacing
     */
        /**
     * Add all members back to the class with appropriate spacing
     */
        /**
     * Add all members back to the class with appropriate spacing
     */
        /**
     * Add all members back to the class with appropriate spacing
     */
        /**
     * Add all members back to the class with appropriate spacing
     */
    private void addMembersWithSpacing(@NotNull Project project, PsiClass psiClass,
                                       List<PsiElement> staticFieldCopies,
                                       List<PsiElement> regularInstanceFieldCopies,
                                       List<PsiElement> annotatedInstanceFieldCopies,
                                       List<PsiElement> methodCopies,
                                       List<PsiElement> innerClassCopies) {

        // Get all existing class members to use as reference points
        PsiElement[] existingChildren = psiClass.getChildren();
        
        // Remove all existing members we're replacing
        List<PsiElement> elementsToRemove = new ArrayList<>();
        for (PsiElement child : existingChildren) {
            if (child instanceof PsiField || child instanceof PsiMethod || child instanceof PsiClass) {
                elementsToRemove.add(child);
            }
        }
        
        // Remove existing elements
        for (PsiElement element : elementsToRemove) {
            element.delete();
        }

        // Add static fields
        PsiElement lastAddedElement = null;
        if (!staticFieldCopies.isEmpty()) {
            for (int i = 0; i < staticFieldCopies.size(); i++) {
                PsiElement element = staticFieldCopies.get(i);
                PsiElement addedElement = psiClass.add(element);
                
                // Add blank line after static fields with Javadoc or annotations (but avoid if followed by group separator)
                // Only add if this is not the last element in its group and the class has other groups
                boolean hasJavadocOrAnnotations = false;
                if (element instanceof PsiField) {
                    hasJavadocOrAnnotations = hasJavadocComment(element) || hasAnnotations((PsiField) element);
                }
                
                if (hasJavadocOrAnnotations && i < staticFieldCopies.size() - 1) {
                    // Add blank line if this is not the last element in the static fields group
                    PsiElement blankLine = createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                } else if (hasJavadocOrAnnotations && i == staticFieldCopies.size() - 1) {
                    // This is the last static field, check if we need a blank line before moving to next group
                    // We'll add group separator later
                }
                lastAddedElement = addedElement;
            }
            
            // Add a blank line between static fields and other member types, if there are other groups
            if (!regularInstanceFieldCopies.isEmpty() || !annotatedInstanceFieldCopies.isEmpty() || 
                !methodCopies.isEmpty() || !innerClassCopies.isEmpty()) {
                PsiElement blankLine = createBlankLine(project);
                if (blankLine != null) {
                    psiClass.addAfter(blankLine, lastAddedElement);
                }
            }
        }

        // Add regular instance fields
        if (!regularInstanceFieldCopies.isEmpty()) {
            for (int i = 0; i < regularInstanceFieldCopies.size(); i++) {
                PsiElement element = regularInstanceFieldCopies.get(i);
                PsiElement addedElement = psiClass.add(element);
                
                // Add blank line after fields with Javadoc (but avoid if followed by group separator)
                // Only add if this is not the last element in its group
                if (element instanceof PsiField && hasJavadocComment(element) && i < regularInstanceFieldCopies.size() - 1) {
                    PsiElement blankLine = createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                } else if (element instanceof PsiField && hasJavadocComment(element) && i == regularInstanceFieldCopies.size() - 1) {
                    // This is the last regular instance field, group separator will be added later if needed
                }
                lastAddedElement = addedElement;
            }
            
            // Add a blank line between regular instance fields and other types, if there are other groups
            if (!annotatedInstanceFieldCopies.isEmpty() || !methodCopies.isEmpty() || !innerClassCopies.isEmpty()) {
                PsiElement blankLine = createBlankLine(project);
                if (blankLine != null) {
                    psiClass.addAfter(blankLine, lastAddedElement);
                }
            }
        }

        // Add annotated instance fields
        if (!annotatedInstanceFieldCopies.isEmpty()) {
            for (int i = 0; i < annotatedInstanceFieldCopies.size(); i++) {
                PsiElement element = annotatedInstanceFieldCopies.get(i);
                PsiElement addedElement = psiClass.add(element);
                
                // Add blank line after annotated fields only if not the last in group
                // Since all annotated fields get special treatment, let's be more selective
                if (i < annotatedInstanceFieldCopies.size() - 1) {
                    // Not the last annotated field in this group
                    PsiElement blankLine = createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                } 
                // The last annotated field will have group separator if needed
                lastAddedElement = addedElement;
            }
            
            // Add a blank line between annotated instance fields and methods/inner classes, if there are other groups
            if (!methodCopies.isEmpty() || !innerClassCopies.isEmpty()) {
                PsiElement blankLine = createBlankLine(project);
                if (blankLine != null) {
                    psiClass.addAfter(blankLine, lastAddedElement);
                }
            }
        }

        // Add methods
        if (!methodCopies.isEmpty()) {
            for (int i = 0; i < methodCopies.size(); i++) {
                PsiElement element = methodCopies.get(i);
                PsiElement addedElement = psiClass.add(element);
                
                // Add blank line after methods with Javadoc (but avoid if followed by group separator)
                // Only add if this is not the last element in its group
                if (element instanceof PsiMethod && hasJavadocComment(element) && i < methodCopies.size() - 1) {
                    PsiElement blankLine = createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                } else if (element instanceof PsiMethod && hasJavadocComment(element) && i == methodCopies.size() - 1) {
                    // This is the last method, group separator will be added later if needed
                }
                lastAddedElement = addedElement;
            }
            
            // Add a blank line between methods and inner classes, if there are inner classes
            if (!innerClassCopies.isEmpty()) {
                PsiElement blankLine = createBlankLine(project);
                if (blankLine != null) {
                    psiClass.addAfter(blankLine, lastAddedElement);
                }
            }
        }
        
        // Add inner classes at the end
        if (!innerClassCopies.isEmpty()) {
            for (int i = 0; i < innerClassCopies.size(); i++) {
                PsiElement element = innerClassCopies.get(i);
                PsiElement addedElement = psiClass.add(element);
                
                // Only add blank line between inner classes, not after the last one
                if (i < innerClassCopies.size() - 1) {
                    PsiElement blankLine = createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                }
                lastAddedElement = addedElement;
            }
        }
        
        // Clean up any excessive whitespace between elements to ensure proper spacing
        cleanupExcessiveWhitespace(psiClass);
    }
    
    /**
     * Helper method to determine if the current element is the last element in the class
     */
    private boolean isLastElement(int currentIndex, List<?> currentGroup, List<?>... otherGroups) {
        // If this is not the last element in the current group, return false
        if (currentIndex < currentGroup.size() - 1) {
            return false;
        }
        
        // Check if any following groups have elements
        for (List<?> group : otherGroups) {
            if (!group.isEmpty()) {
                return false; // There are more elements after this group
            }
        }
        
        // This is the last element in the last group
        return true;
    }

    private PsiElement createBlankLine(@NotNull Project project) {
        try {
            return PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n");
        } catch (Exception e) {
            return null;
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
    
    /**
     * Remove trailing whitespace/blank lines at the end of the class
     */
    private void removeTrailingWhitespace(PsiClass psiClass) {
        // Get a fresh list of children each time since deletion changes the array
        java.util.List<PsiElement> childrenList = new java.util.ArrayList<>();
        for (PsiElement element : psiClass.getChildren()) {
            childrenList.add(element);
        }
        
        // Process from the end to find and remove all trailing whitespace
        while (!childrenList.isEmpty()) {
            PsiElement lastElement = childrenList.get(childrenList.size() - 1);
            
            if (lastElement instanceof com.intellij.psi.PsiWhiteSpace) {
                String text = lastElement.getText();
                if (text.trim().isEmpty()) {  // Only whitespace characters
                    try {
                        lastElement.delete();
                        // Update the list after deletion
                        childrenList = new java.util.ArrayList<>();
                        for (PsiElement element : psiClass.getChildren()) {
                            childrenList.add(element);
                        }
                    } catch (Exception e) {
                        // If deletion fails, stop the cleaning process
                        break;
                    }
                } else {
                    // If the whitespace contains non-whitespace content, stop
                    break;
                }
            } else {
                // If the last element is not whitespace, we're done
                break;
            }
        }
    }
    
    /**
     * Clean up excessive whitespace between elements, keeping only appropriate spacing
     */
    private void cleanupExcessiveWhitespace(PsiClass psiClass) {
        // First, process whitespace between actual members
        PsiElement[] children = psiClass.getChildren();
        
        // Create a list to track elements to be processed
        java.util.List<PsiElement> elementsToProcess = new java.util.ArrayList<>();
        for (PsiElement element : children) {
            elementsToProcess.add(element);
        }
        
        // Process whitespace elements to reduce excessive spacing
        for (int i = 0; i < elementsToProcess.size(); i++) {
            PsiElement current = elementsToProcess.get(i);
            
            // If current element is whitespace, check if it's excessive
            if (current instanceof com.intellij.psi.PsiWhiteSpace) {
                String text = current.getText();
                
                // Count the number of newline characters in the whitespace
                long newlineCount = text.chars().filter(ch -> ch == '\n').count();
                
                // If there are more than 2 newlines (meaning more than one blank line), 
                // we need to reduce it
                if (newlineCount > 2) {
                    try {
                        // Replace with just one blank line (\n\n represents one blank line)
                        PsiElement newWhiteSpace = PsiParserFacade.getInstance(psiClass.getProject())
                            .createWhiteSpaceFromText("\n\n");
                        
                        // Add the new whitespace after the previous element in the original children array
                        // We need to get the current list of children again since the array may have changed
                        PsiElement[] currentChildren = psiClass.getChildren();
                        int currentIndex = -1;
                        for (int j = 0; j < currentChildren.length; j++) {
                            if (currentChildren[j].equals(current)) {
                                currentIndex = j;
                                break;
                            }
                        }
                        
                        if (currentIndex > 0) {
                            PsiElement prevElement = currentChildren[currentIndex - 1];
                            psiClass.addAfter(newWhiteSpace, prevElement);
                        } else {
                            // If this is the first element, add at the beginning
                            psiClass.add(newWhiteSpace);
                        }
                        
                        // Delete the old excessive whitespace
                        current.delete();
                    } catch (Exception e) {
                        // If replacement fails, continue with other elements
                    }
                }
            }
        }
        
        // Perform a final pass to remove trailing whitespace,
        // in case the above operations created new trailing whitespace
        removeTrailingWhitespace(psiClass);
    }
}
