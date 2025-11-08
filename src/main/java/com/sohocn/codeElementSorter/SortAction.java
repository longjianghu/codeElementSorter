package com.sohocn.codeElementSorter;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
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
                
                // Sort the copies
                memberCopies.sort(new CodeElementSortComparator());
                
                // Delete from last to first to maintain text offsets
                membersToDelete.sort((a, b) -> b.getTextRange().getStartOffset() - a.getTextRange().getStartOffset());
                for (PsiMember member : membersToDelete) {
                    member.delete();
                }
                
                // Add sorted members back
                for (PsiMember memberCopy : memberCopies) {
                    psiClass.add(memberCopy);
                }
            } else {
                // For selected members - replace only the selected ones
                // Create copies of members before deleting them
                List<PsiMember> memberCopies = new ArrayList<>();
                for (PsiMember member : membersToSort) {
                    memberCopies.add((PsiMember) member.copy());
                }
                
                // Sort the copies
                memberCopies.sort(new CodeElementSortComparator());
                
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
                    for (PsiMember memberCopy : memberCopies) {
                        psiClass.addAfter(memberCopy, positionMarker);
                        positionMarker = memberCopy;
                    }
                } else {
                    // If no position marker found, just add all members
                    for (PsiMember memberCopy : memberCopies) {
                        psiClass.add(memberCopy);
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