package com.sohocn.codeElementSorter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SortAllAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        PsiClass psiClass = PsiTreeUtil.getChildOfType(psiFile, PsiClass.class);
        if (psiClass == null) return;

        // 使用ElementDetector提取可排序元素
        List<PsiElement> elements = ElementDetector.extractSortableElements(psiClass);

        if (elements.isEmpty()) {
            // 简单提示没有找到可排序的元素
            return;
        }

        // 使用SortingStrategy进行排序
        elements.sort(new SortingStrategy());

        // 在命令操作中重新排列元素
        WriteCommandAction.runWriteCommandAction(project, () -> {
            // 获取 PSI 元素工厂
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

            // 临时存储原始元素以便删除
            List<PsiElement> originalElements = new ArrayList<>(elements);

            // 逐个移动排序后的元素到类的末尾
            for (int i = originalElements.size() - 1; i >= 0; i--) {
                PsiElement elementToMove = originalElements.get(i);
                
                // 使用新工厂创建副本
                if (elementToMove instanceof PsiField) {
                    PsiField field = (PsiField) elementToMove;
                    String fieldText = field.getText();
                    PsiField newField = (PsiField) factory.createFieldFromText(fieldText, psiClass);
                    psiClass.add(newField);
                    elementToMove.delete();
                } else if (elementToMove instanceof PsiMethod) {
                    PsiMethod method = (PsiMethod) elementToMove;
                    String methodText = method.getText();
                    PsiMethod newMethod = (PsiMethod) factory.createMethodFromText(methodText, psiClass);
                    psiClass.add(newMethod);
                    elementToMove.delete();
                }
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 检查当前是否为Java文件
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(psiFile instanceof PsiJavaFile);
    }
}
