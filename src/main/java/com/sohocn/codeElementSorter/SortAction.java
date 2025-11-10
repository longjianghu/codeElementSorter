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
        List<PsiField> regularFieldsCopies = createFieldCopies(regularFields);
        List<PsiField> annotatedFieldsCopies = createFieldCopies(annotatedFields);
        List<PsiMethod> methodsCopies = createMethodCopies(methods);

        // Delete all original sortable members
        deleteMembers(psiClass, sortableMembers);

        // Add members back in the correct order with appropriate spacing
        addMembersWithSpacing(project, psiClass, regularFieldsCopies, annotatedFieldsCopies, methodsCopies);
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

        // Create copies of elements before deleting the originals
        List<PsiElement> copies = new ArrayList<>();
        for (PsiMember member : sortableMembers) {
            copies.add((PsiElement) member.copy());
        }

        // Delete all original selected sortable members
        deleteMembers(psiClass, sortableMembers);

        // Add members back in sorted order
        for (PsiElement copy : copies) {
            psiClass.add(copy);
        }
    }
    
    /**
     * Check if the element has a Javadoc comment
     */
    private boolean hasJavadocComment(PsiElement element) {
        PsiElement prevSibling = element.getPrevSibling();
        while (prevSibling != null) {
            if (prevSibling instanceof PsiComment) {
                String commentText = prevSibling.getText();
                if (commentText.startsWith("/**")) {
                    return true;
                }
            } else if (prevSibling.getNode().getElementType() != com.intellij.psi.JavaTokenType.WHITE_SPACE) {
                // If we encounter a non-comment, non-whitespace element, stop searching
                break;
            }
            prevSibling = prevSibling.getPrevSibling();
        }
        return false;
    }
    
    /**
     * Add all members back to the class with appropriate spacing
     */
    private void addMembersWithSpacing(@NotNull Project project, PsiClass psiClass, 
                                      List<PsiField> regularFieldsCopies,
                                      List<PsiField> annotatedFieldsCopies,
                                      List<PsiMethod> methodsCopies) {
        // Add all regular fields first (if any)
        for (PsiField field : regularFieldsCopies) {
            psiClass.add(field);
            
            // Add blank line after regular field if it has Javadoc comment
            if (hasJavadocComment(field)) {
                try {
                    PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                    psiClass.add(whiteSpace);
                } catch (Exception e) {
                    // Continue without adding whitespace if it fails
                }
            }
        }
        
        // Add annotated fields after regular fields with a blank line if there are annotated fields
        if (!annotatedFieldsCopies.isEmpty()) {
            if (!regularFieldsCopies.isEmpty()) {
                // Add a blank line between regular fields and annotated fields
                try {
                    PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                    psiClass.add(whiteSpace);
                } catch (Exception e) {
                    // Continue without adding whitespace if it fails
                }
            }
            
            for (PsiField field : annotatedFieldsCopies) {
                psiClass.add(field);
                
                // According to README.md: "如果变量使用/**这样的多行注释或带有注解,在变量后面添加一个空行"
                // Add blank line after annotated field (since it has annotations)
                try {
                    PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                    psiClass.add(whiteSpace);
                } catch (Exception e) {
                    // Continue without adding whitespace if it fails
                }
            }
        }
        
        // Add methods after fields with a blank line if there are methods and fields exist
        if (!methodsCopies.isEmpty() && (!regularFieldsCopies.isEmpty() || !annotatedFieldsCopies.isEmpty())) {
            // Add a blank line between fields and methods
            try {
                PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                psiClass.add(whiteSpace);
            } catch (Exception e) {
                // Continue without adding whitespace if it fails
            }
        }
        
        // Add all methods
        for (PsiMethod method : methodsCopies) {
            psiClass.add(method);
            
            // Add blank line after method if it has a Javadoc comment
            if (hasJavadocComment(method)) {
                try {
                    PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                    psiClass.add(whiteSpace);
                } catch (Exception e) {
                    // Continue without adding whitespace if it fails
                }
            }
        }
    }
}
