package com.sohocn.codeElementSorter;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiParserFacade;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class SortAction extends AnAction {

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

        // Sort all members in the current file
        sortAllMembers(project, editor, psiFile);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Enable the action only when in a Java file
        Project project = e.getProject();
        PsiFile psiFile = e.getData(PlatformDataKeys.PSI_FILE);
        
        // Check if we have a project and a psiFile
        if (project == null || psiFile == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        boolean isJavaFile = psiFile instanceof PsiJavaFile;
        e.getPresentation().setEnabledAndVisible(isJavaFile);
    }

    private void sortAllMembers(@NotNull Project project, Editor editor, PsiFile psiFile) {
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
    
    private PsiClass getPsiClassAtOffset(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        return PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
    
    private boolean hasResourceAnnotation(PsiField field) {
        PsiModifierList modifierList = field.getModifierList();
        if (modifierList == null) return false;
        
        // Check if the field has any annotation
        return modifierList.getAnnotations().length > 0;
    }

    private void sortClassMembers(@NotNull Project project, Editor editor, PsiClass psiClass, List<PsiMember> membersToSort) {
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
        
        // Sort the members using the custom sorting strategy
        List<PsiMember> sortedMembers = new ArrayList<>(sortableMembers);
        sortedMembers.sort(new CodeElementSortComparator());
        
        // Apply changes to the file
        WriteCommandAction.runWriteCommandAction(project, () -> {
            if (membersToSort == null) {
                // For all members - collect all sortable members to replace
                List<PsiMember> membersToDelete = new ArrayList<>();
                for (PsiElement element : psiClass.getChildren()) {
                    if (element instanceof PsiMember && 
                        (element instanceof PsiField || element instanceof PsiMethod)) {
                        membersToDelete.add((PsiMember) element);
                    }
                }
                
                // Create copies of members before deleting them
                List<PsiMember> memberCopies = new ArrayList<>();
                for (PsiMember member : membersToDelete) {
                    memberCopies.add((PsiMember) member.copy());
                }
                
                // Separate annotated and non-annotated fields
                List<PsiMember> annotatedFields = new ArrayList<>();
                List<PsiMember> nonAnnotatedFields = new ArrayList<>();
                List<PsiMember> methods = new ArrayList<>();
                
                // Categorize members
                for (PsiMember member : memberCopies) {
                    if (member instanceof PsiField) {
                        if (hasResourceAnnotation((PsiField) member)) {
                            annotatedFields.add(member);
                        } else {
                            nonAnnotatedFields.add(member);
                        }
                    } else if (member instanceof PsiMethod) {
                        methods.add(member);
                    }
                }
                
                // Sort each category
                // For annotated fields and non-annotated fields, we need a comparator that doesn't consider element type
                // since they are already separated
                Comparator<PsiMember> fieldComparator = new Comparator<PsiMember>() {
                    @Override
                    public int compare(PsiMember member1, PsiMember member2) {
                        // Second priority: Visibility (public before package-private before protected before private)
                        int visibilityComparison = compareByVisibility(member1, member2);
                        if (visibilityComparison != 0) {
                            return visibilityComparison;
                        }
                        
                        // Third priority: Name in alphabetical order (case-insensitive)
                        return compareByName(member1, member2);
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
                };
                
                annotatedFields.sort(fieldComparator);
                nonAnnotatedFields.sort(fieldComparator);
                methods.sort(new CodeElementSortComparator());
                
                // Combine in order: annotated fields, non-annotated fields, methods
                memberCopies.clear();
                memberCopies.addAll(annotatedFields);
                // Add a separator between annotated and non-annotated fields if both exist
                boolean needsSeparator = !annotatedFields.isEmpty() && (!nonAnnotatedFields.isEmpty() || !methods.isEmpty());
                memberCopies.addAll(nonAnnotatedFields);
                memberCopies.addAll(methods);
                
                // Delete from last to first to maintain text offsets
                membersToDelete.sort((a, b) -> b.getTextRange().getStartOffset() - a.getTextRange().getStartOffset());
                for (PsiMember member : membersToDelete) {
                    member.delete();
                }
                
                // Add sorted members back
                // First, add all annotated fields
                PsiElement lastAdded = null;
                for (int i = 0; i < annotatedFields.size(); i++) {
                    PsiMember memberCopy = annotatedFields.get(i);
                    if (lastAdded == null) {
                        lastAdded = psiClass.add(memberCopy);
                    } else {
                        lastAdded = psiClass.addAfter(memberCopy, lastAdded);
                    }
                }
                
                // Add separator between annotated and non-annotated fields if needed
                if (needsSeparator) {
                    try {
                        PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                        lastAdded = psiClass.addAfter(whiteSpace, lastAdded);
                    } catch (Exception e) {
                        // Ignore if we can't add whitespace
                    }
                }
                
                // Then add non-annotated fields
                for (int i = 0; i < nonAnnotatedFields.size(); i++) {
                    PsiMember memberCopy = nonAnnotatedFields.get(i);
                    if (lastAdded == null && annotatedFields.isEmpty()) {
                        lastAdded = psiClass.add(memberCopy);
                    } else {
                        lastAdded = psiClass.addAfter(memberCopy, lastAdded);
                    }
                }
                
                // Finally add methods
                for (int i = 0; i < methods.size(); i++) {
                    PsiMember memberCopy = methods.get(i);
                    if (lastAdded == null && annotatedFields.isEmpty() && nonAnnotatedFields.isEmpty()) {
                        lastAdded = psiClass.add(memberCopy);
                    } else {
                        lastAdded = psiClass.addAfter(memberCopy, lastAdded);
                    }
                }
            } else {
                // For selected members - replace only the selected ones
                // Create copies of members before deleting them
                List<PsiMember> memberCopies = new ArrayList<>();
                for (PsiMember member : membersToSort) {
                    memberCopies.add((PsiMember) member.copy());
                }
                
                // Separate annotated and non-annotated fields
                List<PsiMember> annotatedFields = new ArrayList<>();
                List<PsiMember> nonAnnotatedFields = new ArrayList<>();
                List<PsiMember> methods = new ArrayList<>();
                
                // Categorize members
                for (PsiMember member : memberCopies) {
                    if (member instanceof PsiField) {
                        if (hasResourceAnnotation((PsiField) member)) {
                            annotatedFields.add(member);
                        } else {
                            nonAnnotatedFields.add(member);
                        }
                    } else if (member instanceof PsiMethod) {
                        methods.add(member);
                    }
                }
                
                // Sort each category
                // For annotated fields and non-annotated fields, we need a comparator that doesn't consider element type
                // since they are already separated
                Comparator<PsiMember> fieldComparator = new Comparator<PsiMember>() {
                    @Override
                    public int compare(PsiMember member1, PsiMember member2) {
                        // Second priority: Visibility (public before package-private before protected before private)
                        int visibilityComparison = compareByVisibility(member1, member2);
                        if (visibilityComparison != 0) {
                            return visibilityComparison;
                        }
                        
                        // Third priority: Name in alphabetical order (case-insensitive)
                        return compareByName(member1, member2);
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
                };
                
                annotatedFields.sort(fieldComparator);
                nonAnnotatedFields.sort(fieldComparator);
                methods.sort(new CodeElementSortComparator());
                
                // Combine in order: annotated fields, non-annotated fields, methods
                memberCopies.clear();
                memberCopies.addAll(annotatedFields);
                // Add a separator between annotated and non-annotated fields if both exist
                boolean needsSeparator = !annotatedFields.isEmpty() && (!nonAnnotatedFields.isEmpty() || !methods.isEmpty());
                memberCopies.addAll(nonAnnotatedFields);
                memberCopies.addAll(methods);
                
                // Find the position to insert the sorted members
                PsiElement positionMarker = null;
                for (PsiElement element : psiClass.getChildren()) {
                    if (element.getTextOffset() >= membersToSort.get(0).getTextOffset()) {
                        positionMarker = element.getPrevSibling();
                        break;
                    }
                }
                
                // Delete the selected members
                for (PsiMember member : membersToSort) {
                    member.delete();
                }
                
                // Insert sorted members at the appropriate position
                if (positionMarker != null) {
                    PsiElement lastAdded = positionMarker;
                    
                    // First, add all annotated fields
                    for (int i = 0; i < annotatedFields.size(); i++) {
                        PsiMember memberCopy = annotatedFields.get(i);
                        lastAdded = psiClass.addAfter(memberCopy, lastAdded);
                    }
                    
                    // Add separator between annotated and non-annotated fields if needed
                    if (needsSeparator) {
                        try {
                            PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                            lastAdded = psiClass.addAfter(whiteSpace, lastAdded);
                        } catch (Exception e) {
                            // Ignore if we can't add whitespace
                        }
                    }
                    
                    // Then add non-annotated fields
                    for (int i = 0; i < nonAnnotatedFields.size(); i++) {
                        PsiMember memberCopy = nonAnnotatedFields.get(i);
                        lastAdded = psiClass.addAfter(memberCopy, lastAdded);
                    }
                    
                    // Finally add methods
                    for (int i = 0; i < methods.size(); i++) {
                        PsiMember memberCopy = methods.get(i);
                        lastAdded = psiClass.addAfter(memberCopy, lastAdded);
                    }
                } else {
                    // If no position marker found, just add all members
                    // First, add all annotated fields
                    PsiElement lastAdded = null;
                    for (int i = 0; i < annotatedFields.size(); i++) {
                        PsiMember memberCopy = annotatedFields.get(i);
                        if (lastAdded == null) {
                            lastAdded = psiClass.add(memberCopy);
                        } else {
                            lastAdded = psiClass.addAfter(memberCopy, lastAdded);
                        }
                    }
                    
                    // Add separator between annotated and non-annotated fields if needed
                    if (needsSeparator) {
                        try {
                            PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                            lastAdded = psiClass.addAfter(whiteSpace, lastAdded);
                        } catch (Exception e) {
                            // Ignore if we can't add whitespace
                        }
                    }
                    
                    // Then add non-annotated fields
                    for (int i = 0; i < nonAnnotatedFields.size(); i++) {
                        PsiMember memberCopy = nonAnnotatedFields.get(i);
                        if (lastAdded == null && annotatedFields.isEmpty()) {
                            lastAdded = psiClass.add(memberCopy);
                        } else {
                            lastAdded = psiClass.addAfter(memberCopy, lastAdded);
                        }
                    }
                    
                    // Finally add methods
                    for (int i = 0; i < methods.size(); i++) {
                        PsiMember memberCopy = methods.get(i);
                        if (lastAdded == null && annotatedFields.isEmpty() && nonAnnotatedFields.isEmpty()) {
                            lastAdded = psiClass.add(memberCopy);
                        } else {
                            lastAdded = psiClass.addAfter(memberCopy, lastAdded);
                        }
                    }
                }
            }
        });
        
        // Show appropriate message
        if (membersToSort != null) {
            Messages.showInfoMessage("Sorted " + sortedMembers.size() + " selected elements", "Success");
        } else {
            // Count fields and methods
            long fieldCount = sortedMembers.stream().filter(m -> m instanceof PsiField).count();
            long methodCount = sortedMembers.stream().filter(m -> m instanceof PsiMethod).count();
            Messages.showInfoMessage("Sorted " + sortedMembers.size() + " elements: " + 
                    fieldCount + " fields, " + methodCount + " methods", "Success");
        }
    }
}