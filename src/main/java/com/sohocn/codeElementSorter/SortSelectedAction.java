package com.sohocn.codeElementSorter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SortSelectedAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) return;

        // 获取选择的文本范围
        int startOffset = editor.getSelectionModel().getSelectionStart();
        int endOffset = editor.getSelectionModel().getSelectionEnd();

        if (startOffset == endOffset) return;

        // 使用改进的 ElementDetector 提取选择区域的元素
        List<PsiElement> selectedElements = ElementDetector.extractElementsFromSelection(psiFile, startOffset, endOffset);

        if (selectedElements.isEmpty()) {
            // 没有找到可排序的元素
            return;
        }

        // 使用SortingStrategy进行排序
        selectedElements.sort(new SortingStrategy());

        // 重新构建选择区域的内容
        StringBuilder newContent = new StringBuilder();
        for (int i = 0; i < selectedElements.size(); i++) {
            if (i > 0) newContent.append("\n");
            newContent.append(selectedElements.get(i).getText());
        }

        // 应用更改到选择区域
        WriteCommandAction.runWriteCommandAction(project, () -> {
            editor.getDocument().replaceString(startOffset, endOffset, newContent.toString());
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只有当有文本被选中时才启用此操作
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean hasSelection = editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabledAndVisible(hasSelection);
    }
}
