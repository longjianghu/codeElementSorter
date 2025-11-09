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
                // Group fields and methods separately according to README rules
                // Group 1: Regular variables (sorted by visibility and name) - following README order
                List<PsiField> regularFields = sortableMembers.stream()
                    .filter(m -> m instanceof PsiField)
                    .map(m -> (PsiField) m)
                    .filter(field -> field.getAnnotations().length == 0)
                    .collect(Collectors.toList());
                
                // Group 2: Annotation variables (sorted by visibility and name) - following README order
                List<PsiField> annotatedFields = sortableMembers.stream()
                    .filter(m -> m instanceof PsiField)
                    .map(m -> (PsiField) m)
                    .filter(field -> field.getAnnotations().length > 0)
                    .collect(Collectors.toList());
                
                // Group 3: Methods (sorted by visibility and name)
                List<PsiMethod> methods = sortableMembers.stream()
                    .filter(m -> m instanceof PsiMethod)
                    .map(m -> (PsiMethod) m)
                    .collect(Collectors.toList());
                
                // Sort each group using the comparator
                regularFields.sort(new CodeElementSortComparator());
                annotatedFields.sort(new CodeElementSortComparator());
                methods.sort(new CodeElementSortComparator());
                
                // Create copies of elements before deleting the originals
                List<PsiField> regularFieldsCopies = new ArrayList<>();
                for (PsiField field : regularFields) {
                    regularFieldsCopies.add((PsiField) field.copy());
                }
                
                List<PsiField> annotatedFieldsCopies = new ArrayList<>();
                for (PsiField field : annotatedFields) {
                    annotatedFieldsCopies.add((PsiField) field.copy());
                }
                
                List<PsiMethod> methodsCopies = new ArrayList<>();
                for (PsiMethod method : methods) {
                    methodsCopies.add((PsiMethod) method.copy());
                }
                
                // Delete all original sortable members
                List<PsiMember> membersToDelete = new ArrayList<>();
                for (PsiElement element : psiClass.getChildren()) {
                    if (element instanceof PsiMember &&
                        (element instanceof PsiField || element instanceof PsiMethod)) {
                        membersToDelete.add((PsiMember) element);
                    }
                }
                
                // Delete from last to first to maintain text offsets
                membersToDelete.sort((a, b) -> b.getTextRange().getStartOffset() - a.getTextRange().getStartOffset());
                for (PsiMember member : membersToDelete) {
                    member.delete();
                }

                // Add regular fields first (if any)
                PsiElement lastAdded = null;
                for (int i = 0; i < regularFieldsCopies.size(); i++) {
                    PsiField field = regularFieldsCopies.get(i);
                    if (lastAdded == null) {
                        lastAdded = psiClass.add(field);
                    } else {
                        lastAdded = psiClass.addAfter(field, lastAdded);
                    }
                }
                
                // Add annotated fields after regular fields with a blank line if there are annotated fields
                if (!annotatedFieldsCopies.isEmpty()) {
                    if (regularFieldsCopies.size() > 0) {
                        // Add a blank line between regular fields and annotated fields
                        try {
                            PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                            lastAdded = psiClass.addAfter(whiteSpace, lastAdded);
                        } catch (Exception e) {
                            // Continue without adding whitespace if it fails
                        }
                    }
                    
                    for (int i = 0; i < annotatedFieldsCopies.size(); i++) {
                        PsiField field = annotatedFieldsCopies.get(i);
                        if (lastAdded == null && regularFieldsCopies.isEmpty()) {
                            lastAdded = psiClass.add(field);
                        } else {
                            lastAdded = psiClass.addAfter(field, lastAdded);
                        }
                        
                        // Add blank line after each annotated field (if not the last one and there are more elements following)
                        if (i < annotatedFieldsCopies.size() - 1 || regularFieldsCopies.size() > 0 || methodsCopies.size() > 0) {
                            try {
                                PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                                lastAdded = psiClass.addAfter(whiteSpace, lastAdded);
                            } catch (Exception e) {
                                // Continue without adding whitespace if it fails
                            }
                        }
                    }
                }
                
                // Add methods after fields with a blank line if there are methods
                if (!methodsCopies.isEmpty()) {
                    if ((regularFieldsCopies.size() > 0 || annotatedFieldsCopies.size() > 0)) {
                        // Add a blank line between fields and methods
                        try {
                            PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                            lastAdded = psiClass.addAfter(whiteSpace, lastAdded);
                        } catch (Exception e) {
                            // Continue without adding whitespace if it fails
                        }
                    }
                    
                    for (PsiMethod method : methodsCopies) {
                        if (lastAdded == null && regularFieldsCopies.isEmpty() && annotatedFieldsCopies.isEmpty()) {
                            lastAdded = psiClass.add(method);
                        } else {
                            lastAdded = psiClass.addAfter(method, lastAdded);
                        }
                    }
                }
            } else {
                // For selected members - replace only the selected ones
                // Sort the members using the custom sorting strategy
                List<PsiMember> sortedMembers = new ArrayList<>(membersToSort);
                sortedMembers.sort(new CodeElementSortComparator());

                // Create copies before deletion
                List<PsiMember> sortedCopies = new ArrayList<>();
                for (PsiMember member : sortedMembers) {
                    if (member instanceof PsiField) {
                        sortedCopies.add((PsiMember) ((PsiField) member).copy());
                    } else if (member instanceof PsiMethod) {
                        sortedCopies.add((PsiMember) ((PsiMethod) member).copy());
                    }
                }

                // Find the position to insert the sorted members
                PsiElement positionMarker = null;
                for (PsiElement element : psiClass.getChildren()) {
                    if (element.getTextOffset() >= membersToSort.get(0).getTextOffset()) {
                        positionMarker = element.getPrevSibling();
                        break;
                    }
                }

                // Delete the selected members
                // Sort the membersToDelete from last to first to maintain text offsets
                List<PsiMember> membersToDelete = new ArrayList<>(membersToSort);
                membersToDelete.sort((a, b) -> b.getTextRange().getStartOffset() - a.getTextRange().getStartOffset());
                for (PsiMember member : membersToDelete) {
                    member.delete();
                }

                // Insert sorted members at the appropriate position
                if (positionMarker != null) {
                    for (PsiMember member : sortedCopies) {
                        positionMarker = psiClass.addAfter(member, positionMarker);
                    }
                } else {
                    // If no position marker found, just add all members
                    for (PsiMember member : sortedCopies) {
                        psiClass.add(member);
                    }
                }
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
}

