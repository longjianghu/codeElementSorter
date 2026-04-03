package com.sohocn.codeElementSorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;

/**
 * The type Sort action.
 *
 * @author longjianghu
 */
public class SortAction extends AnAction {
    // 单例 Comparator，避免重复创建
    private static final CodeElementSortComparator COMPARATOR = new CodeElementSortComparator();

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

        if (selectionModel.hasSelection()) {
            this.sortSelectedMembers(project, editor, psiFile);
        } else {
            this.sortAllMembers(project, psiFile);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * Sort all members.
     *
     * @param project
     *            the project
     * @param psiFile
     *            the psi file
     */
    public void sortAllMembers(@NotNull Project project, PsiFile psiFile) {
        PsiJavaFile javaFile = (PsiJavaFile)psiFile;
        PsiClass[] classes = javaFile.getClasses();

        if (classes.length == 0) {
            Messages.showInfoMessage("No classes found in file", "Info");
            return;
        }

        PsiClass psiClass = classes[0];
        this.sortClassMembers(project, psiClass, null);
    }

    /**
     * Sort class members.
     *
     * @param project
     *            the project
     * @param psiClass
     *            the psi class
     * @param membersToSort
     *            the members to sort
     */
    public void sortClassMembers(@NotNull Project project, PsiClass psiClass, List<PsiMember> membersToSort) {
        List<PsiMember> members;

        if (membersToSort == null) {
            members = new ArrayList<>();
            for (PsiElement element : psiClass.getChildren()) {
                if ((element instanceof PsiField || element instanceof PsiMethod)) {
                    members.add((PsiMember)element);
                }
            }
        } else {
            members = membersToSort;
        }

        List<PsiMember> sortableMembers = members
            .stream()
            .filter(member -> member instanceof PsiField || member instanceof PsiMethod)
            .collect(Collectors.toList());

        if (sortableMembers.isEmpty()) {
            Messages.showInfoMessage("No sortable elements found", "Info");
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            if (membersToSort == null) {
                this.performFullSorting(project, psiClass, sortableMembers);
            } else {
                this.performSelectedSorting(psiClass, membersToSort);
            }
        });

        if (membersToSort != null) {
            Messages.showInfoMessage("Sorted " + sortableMembers.size() + " selected elements", "Success");
        } else {
            long fieldCount = sortableMembers.stream().filter(m -> m instanceof PsiField).count();
            long methodCount = sortableMembers.stream().filter(m -> m instanceof PsiMethod).count();
            Messages
                .showInfoMessage("Sorted " + sortableMembers.size() + " elements: " + fieldCount + " fields, "
                    + methodCount + " methods", "Success");
        }
    }

    /**
     * Sort selected members.
     *
     * @param project
     *            the project
     * @param editor
     *            the editor
     * @param psiFile
     *            the psi file
     */
    public void sortSelectedMembers(@NotNull Project project, Editor editor, PsiFile psiFile) {
        PsiJavaFile javaFile = (PsiJavaFile)psiFile;
        PsiClass[] classes = javaFile.getClasses();

        if (classes.length == 0) {
            Messages.showInfoMessage("No classes found in file", "Info");
            return;
        }

        PsiClass psiClass = classes[0];
        if (psiClass == null) {
            Messages.showInfoMessage("No class found in file", "Info");
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        int startOffset = selectionModel.getSelectionStart();
        int endOffset = selectionModel.getSelectionEnd();

        List<PsiMember> membersToSort = new ArrayList<>();
        for (PsiElement element : psiClass.getChildren()) {
            if ((element instanceof PsiField || element instanceof PsiMethod)) {
                int memberStart = element.getTextRange().getStartOffset();
                int memberEnd = element.getTextRange().getEndOffset();

                if (memberStart >= startOffset && memberEnd <= endOffset) {
                    membersToSort.add((PsiMember)element);
                }
            }
        }

        if (membersToSort.isEmpty()) {
            Messages.showInfoMessage("No sortable elements found in selection", "Info");
            return;
        }

        this.sortClassMembers(project, psiClass, membersToSort);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(PlatformDataKeys.PSI_FILE);
        boolean isJavaFile = psiFile instanceof PsiJavaFile;
        e.getPresentation().setEnabledAndVisible(isJavaFile);
    }

    private void addMembersWithSpacing(@NotNull Project project, PsiClass psiClass, List<PsiElement> staticFieldCopies,
                                       List<PsiElement> regularInstanceFieldCopies, List<PsiElement> annotatedInstanceFieldCopies,
                                       List<PsiElement> methodCopies, List<PsiElement> innerClassCopies) {

        PsiElement[] existingChildren = psiClass.getChildren();

        List<PsiElement> elementsToRemove = new ArrayList<>();
        for (PsiElement child : existingChildren) {
            if (child instanceof PsiField || child instanceof PsiMethod || child instanceof PsiClass) {
                elementsToRemove.add(child);
            }
        }

        for (PsiElement element : elementsToRemove) {
            element.delete();
        }

        PsiElement lastAddedElement = null;
        if (!staticFieldCopies.isEmpty()) {
            for (int i = 0; i < staticFieldCopies.size(); i++) {
                PsiElement element = staticFieldCopies.get(i);
                PsiElement addedElement = psiClass.add(element);

                boolean hasJavadocOrAnnotations = false;
                if (element instanceof PsiField) {
                    hasJavadocOrAnnotations = this.hasJavadocComment(element) || this.hasAnnotations((PsiField)element);
                }

                if (hasJavadocOrAnnotations && i < staticFieldCopies.size() - 1) {
                    PsiElement blankLine = this.createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                }

                lastAddedElement = addedElement;
            }

            if (!regularInstanceFieldCopies.isEmpty() || !annotatedInstanceFieldCopies.isEmpty()
                || !methodCopies.isEmpty() || !innerClassCopies.isEmpty()) {
                PsiElement blankLine = this.createBlankLine(project);
                if (blankLine != null) {
                    psiClass.addAfter(blankLine, lastAddedElement);
                }
            }
        }

        if (!regularInstanceFieldCopies.isEmpty()) {
            for (int i = 0; i < regularInstanceFieldCopies.size(); i++) {
                PsiElement element = regularInstanceFieldCopies.get(i);
                PsiElement addedElement = psiClass.add(element);

                if (element instanceof PsiField && this.hasJavadocComment(element)
                    && i < regularInstanceFieldCopies.size() - 1) {
                    PsiElement blankLine = this.createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                } else {
                    if (element instanceof PsiField) {
                        this.hasJavadocComment(element);
                    }
                }
                lastAddedElement = addedElement;
            }

            if (!annotatedInstanceFieldCopies.isEmpty() || !methodCopies.isEmpty() || !innerClassCopies.isEmpty()) {
                PsiElement blankLine = this.createBlankLine(project);
                if (blankLine != null) {
                    psiClass.addAfter(blankLine, lastAddedElement);
                }
            }
        }

        if (!annotatedInstanceFieldCopies.isEmpty()) {
            for (int i = 0; i < annotatedInstanceFieldCopies.size(); i++) {
                PsiElement element = annotatedInstanceFieldCopies.get(i);
                PsiElement addedElement = psiClass.add(element);

                if (i < annotatedInstanceFieldCopies.size() - 1) {
                    PsiElement blankLine = this.createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                }
                lastAddedElement = addedElement;
            }

            if (!methodCopies.isEmpty() || !innerClassCopies.isEmpty()) {
                PsiElement blankLine = this.createBlankLine(project);
                if (blankLine != null) {
                    psiClass.addAfter(blankLine, lastAddedElement);
                }
            }
        }

        if (!methodCopies.isEmpty()) {
            for (int i = 0; i < methodCopies.size(); i++) {
                PsiElement element = methodCopies.get(i);
                PsiElement addedElement = psiClass.add(element);

                if (element instanceof PsiMethod && this.hasJavadocComment(element) && i < methodCopies.size() - 1) {
                    PsiElement blankLine = this.createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                } else {
                    if (element instanceof PsiMethod) {
                        this.hasJavadocComment(element);
                    }
                }
                lastAddedElement = addedElement;
            }

            if (!innerClassCopies.isEmpty()) {
                PsiElement blankLine = this.createBlankLine(project);
                if (blankLine != null) {
                    psiClass.addAfter(blankLine, lastAddedElement);
                }
            }
        }

        if (!innerClassCopies.isEmpty()) {
            for (int i = 0; i < innerClassCopies.size(); i++) {
                PsiElement element = innerClassCopies.get(i);
                PsiElement addedElement = psiClass.add(element);

                if (i < innerClassCopies.size() - 1) {
                    PsiElement blankLine = this.createBlankLine(project);
                    if (blankLine != null) {
                        psiClass.addAfter(blankLine, addedElement);
                    }
                }
            }
        }

        this.cleanupExcessiveWhitespace(psiClass);
    }

    private void cleanupExcessiveWhitespace(PsiClass psiClass) {
        PsiElement[] children = psiClass.getChildren();

        List<PsiElement> elementsToProcess = new ArrayList<>(Arrays.asList(children));

        for (PsiElement current : elementsToProcess) {
            if (current instanceof PsiWhiteSpace) {
                String text = current.getText();

                long newlineCount = text.chars().filter(ch -> ch == '\n').count();

                if (newlineCount > 2) {
                    try {
                        PsiElement newWhiteSpace =
                            PsiParserFacade.getInstance(psiClass.getProject()).createWhiteSpaceFromText("\n\n");

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
                            psiClass.add(newWhiteSpace);
                        }

                        current.delete();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        this.removeTrailingWhitespace(psiClass);
    }

    private PsiElement createBlankLine(@NotNull Project project) {
        try {
            return PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n");
        } catch (Exception e) {
            return null;
        }
    }

    private List<PsiElement> createElementCopiesWithComments(List<? extends PsiMember> members) {
        List<PsiElement> copies = new ArrayList<>();
        for (PsiMember member : members) {
            List<PsiElement> relatedElements = this.getRelatedElements(member);
            for (PsiElement element : relatedElements) {
                copies.add(element.copy());
            }
            copies.add(member.copy());
        }
        return copies;
    }

    /**
     * 优化版本：一次遍历完成复制，并缓存 relatedElements
     */
    private List<PsiElement> createElementCopiesWithRelatedElements(List<? extends PsiMember> members) {
        List<PsiElement> allCopies = new ArrayList<>(members.size() * 2);
        for (PsiMember member : members) {
            if (member.isValid()) {
                List<PsiElement> relatedElements = this.getRelatedElements(member);
                for (PsiElement element : relatedElements) {
                    if (element.isValid()) {
                        allCopies.add(element.copy());
                    }
                }
                allCopies.add(member.copy());
            }
        }
        return allCopies;
    }

    /**
     * 优化版本：使用缓存的 relatedElements map，避免重复遍历
     */
    private void deleteMembersWithComments(List<PsiMember> membersToDelete) {
        if (membersToDelete.isEmpty()) {
            return;
        }

        // 按位置排序，从后往前删除
        membersToDelete.sort((a, b) -> Integer.compare(
            b.getTextRange().getStartOffset(),
            a.getTextRange().getStartOffset()
        ));

        for (PsiMember member : membersToDelete) {
            if (!member.isValid()) {
                continue;
            }

            List<PsiElement> relatedElements = this.getRelatedElements(member);
            for (PsiElement element : relatedElements) {
                if (element.isValid()) {
                    element.delete();
                }
            }

            if (member.isValid()) {
                member.delete();
            }
        }
    }

    private List<PsiElement> getRelatedElements(PsiMember member) {
        List<PsiElement> relatedElements = new ArrayList<>();

        PsiElement prevSibling = member.getPrevSibling();
        while (prevSibling != null) {
            if (prevSibling instanceof PsiComment || prevSibling instanceof PsiWhiteSpace) {
                relatedElements.add(0, prevSibling);
                prevSibling = prevSibling.getPrevSibling();
            } else {
                break;
            }
        }

        return relatedElements;
    }

    private boolean hasAnnotations(PsiField field) {
        return field.getAnnotations().length > 0;
    }

    private boolean hasJavadocComment(PsiElement element) {
        if (element instanceof PsiDocCommentOwner) {
            return ((PsiDocCommentOwner)element).getDocComment() != null;
        }
        return false;
    }

    private void performFullSorting(@NotNull Project project, PsiClass psiClass, List<PsiMember> sortableMembers) {
        this.performFullSortingWithDepth(project, psiClass, sortableMembers, 0);
    }

    /**
     * 优化版本：一次遍历完成所有分类
     */
    private void performFullSortingWithDepth(@NotNull Project project, PsiClass psiClass,
                                             List<PsiMember> sortableMembers, int depth) {

        if (depth >= 3) {
            return;
        }

        // 优化：一次遍历完成分类
        List<PsiField> staticFields = new ArrayList<>();
        List<PsiField> regularInstanceFields = new ArrayList<>();
        List<PsiField> annotatedInstanceFields = new ArrayList<>();
        List<PsiMethod> methods = new ArrayList<>();

        for (PsiMember member : sortableMembers) {
            if (member instanceof PsiField) {
                PsiField field = (PsiField) member;
                if (field.hasModifierProperty(PsiModifier.STATIC)) {
                    staticFields.add(field);
                } else if (field.getAnnotations().length > 0) {
                    annotatedInstanceFields.add(field);
                } else {
                    regularInstanceFields.add(field);
                }
            } else if (member instanceof PsiMethod) {
                methods.add((PsiMethod) member);
            }
        }

        // 查找内部类
        List<PsiClass> innerClasses = new ArrayList<>();
        for (PsiElement element : psiClass.getChildren()) {
            if (element instanceof PsiClass && element != psiClass) {
                innerClasses.add((PsiClass)element);
                if (depth < 2) {
                    List<PsiMember> innerSortableMembers = new ArrayList<>();
                    for (PsiElement innerElement : element.getChildren()) {
                        if ((innerElement instanceof PsiField || innerElement instanceof PsiMethod)) {
                            innerSortableMembers.add((PsiMember)innerElement);
                        }
                    }
                    this.performFullSortingWithDepth(project, (PsiClass)element, innerSortableMembers, depth + 1);
                }
            }
        }

        // 使用单例 Comparator 进行排序
        staticFields.sort(COMPARATOR);
        regularInstanceFields.sort(COMPARATOR);
        annotatedInstanceFields.sort(COMPARATOR);
        methods.sort(COMPARATOR);

        List<PsiElement> staticFieldCopies = this.createElementCopiesWithRelatedElements(staticFields);
        List<PsiElement> regularInstanceFieldCopies =
            this.createElementCopiesWithRelatedElements(regularInstanceFields);
        List<PsiElement> annotatedInstanceFieldCopies =
            this.createElementCopiesWithRelatedElements(annotatedInstanceFields);
        List<PsiElement> methodCopies = this.createElementCopiesWithRelatedElements(methods);

        List<PsiElement> innerClassCopies = new ArrayList<>(innerClasses.size());
        for (PsiClass innerClass : innerClasses) {
            innerClassCopies.add(innerClass.copy());
        }

        List<PsiMember> allMembersToDelete = new ArrayList<>(sortableMembers);
        allMembersToDelete.addAll(innerClasses);
        this.deleteMembersWithComments(allMembersToDelete);

        this
            .addMembersWithSpacing(project, psiClass, staticFieldCopies, regularInstanceFieldCopies,
                annotatedInstanceFieldCopies, methodCopies, innerClassCopies);
    }

    /**
     * 优化版本：减少遍历次数
     */
    private void performSelectedSorting(PsiClass psiClass, List<PsiMember> membersToSort) {
        // 过滤并获取选择区域的起始和结束位置
        List<PsiMember> sortableMembers = new ArrayList<>();
        int minStartOffset = Integer.MAX_VALUE;
        int maxEndOffset = 0;

        for (PsiMember member : membersToSort) {
            if (member instanceof PsiField || member instanceof PsiMethod) {
                sortableMembers.add(member);
                int startOffset = member.getTextRange().getStartOffset();
                int endOffset = member.getTextRange().getEndOffset();
                if (startOffset < minStartOffset) {
                    minStartOffset = startOffset;
                }
                if (endOffset > maxEndOffset) {
                    maxEndOffset = endOffset;
                }
            }
        }

        if (sortableMembers.isEmpty()) {
            return;
        }

        // 查找插入位置：第一个被选中的成员的位置
        PsiElement insertAnchor = null;
        PsiElement firstMember = sortableMembers.get(0);

        // 找到第一个成员前面的有效元素作为插入点
        PsiElement prevSibling = firstMember.getPrevSibling();
        while (prevSibling != null) {
            if (prevSibling instanceof PsiWhiteSpace) {
                // 保留第一个成员前面的单个换行
                String whitespaceText = prevSibling.getText();
                int newlineCount = (int) whitespaceText.chars().filter(ch -> ch == '\n').count();
                if (newlineCount > 1) {
                    // 多个换行，只保留一个
                    PsiParserFacade.getInstance(psiClass.getProject())
                        .createWhiteSpaceFromText("\n");
                    prevSibling.replace(PsiParserFacade.getInstance(psiClass.getProject())
                        .createWhiteSpaceFromText("\n"));
                }
                break;
            } else if (prevSibling instanceof PsiComment) {
                break;
            }
            prevSibling = prevSibling.getPrevSibling();
        }

        // 找到插入锚点（第一个成员）
        insertAnchor = firstMember;

        // 使用单例 Comparator 排序
        sortableMembers.sort(COMPARATOR);

        // 只复制成员本身
        List<PsiElement> copies = new ArrayList<>(sortableMembers.size());
        for (PsiMember member : sortableMembers) {
            copies.add(member.copy());
        }

        // 按相反顺序删除，保持 PSI 有效性
        List<PsiMember> toDelete = new ArrayList<>(sortableMembers);
        toDelete.sort((a, b) -> Integer.compare(
            b.getTextRange().getStartOffset(),
            a.getTextRange().getStartOffset()
        ));

        for (PsiMember member : toDelete) {
            if (member.isValid()) {
                member.delete();
            }
        }

        // 在原来的位置插入排序后的成员
        if (insertAnchor != null && insertAnchor.isValid()) {
            // 删除插入点前的多余空白
            PsiElement anchorPrev = insertAnchor.getPrevSibling();
            if (anchorPrev instanceof PsiWhiteSpace) {
                String wsText = anchorPrev.getText();
                int newlines = (int) wsText.chars().filter(ch -> ch == '\n').count();
                if (newlines > 1) {
                    anchorPrev.replace(PsiParserFacade.getInstance(psiClass.getProject())
                        .createWhiteSpaceFromText("\n"));
                }
            }

            for (PsiElement copy : copies) {
                psiClass.addBefore(copy, insertAnchor);
            }
        } else {
            for (PsiElement copy : copies) {
                psiClass.add(copy);
            }
        }
    }

    private void removeTrailingWhitespace(PsiClass psiClass) {
        List<PsiElement> childrenList = new ArrayList<>(Arrays.asList(psiClass.getChildren()));

        while (!childrenList.isEmpty()) {
            PsiElement lastElement = childrenList.get(childrenList.size() - 1);

            if (lastElement instanceof PsiWhiteSpace) {
                String text = lastElement.getText();
                if (text.trim().isEmpty()) {
                    try {
                        lastElement.delete();
                        childrenList = new ArrayList<>(Arrays.asList(psiClass.getChildren()));
                    } catch (Exception e) {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }
    }
}
