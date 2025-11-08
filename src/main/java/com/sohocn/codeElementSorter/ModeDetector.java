package com.sohocn.codeElementSorter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;

public class ModeDetector {
    
    /**
     * 检测操作模式
     * @param editor 编辑器实例
     * @param psiFile Psi文件
     * @return 操作模式（true=全页面排序，false=选择区域排序）
     */
    public static boolean detectMode(Editor editor, PsiFile psiFile) {
        // 检查是否有选择文本
        if (editor.getSelectionModel().hasSelection()) {
            // 检查选择是否包含完整的元素定义
            if (isSelectionValid(editor, psiFile)) {
                return false; // 选择区域排序模式
            }
        }
        
        return true; // 全页面排序模式
    }
    
    /**
     * 验证选择区域是否有效（包含完整元素）
     * @param editor 编辑器实例
     * @param psiFile Psi文件
     * @return 选择是否有效
     */
    private static boolean isSelectionValid(Editor editor, PsiFile psiFile) {
        int start = editor.getSelectionModel().getSelectionStart();
        int end = editor.getSelectionModel().getSelectionEnd();
        
        // 获取选择范围内的文本
        String selectedText = editor.getDocument().getText(new TextRange(start, end));
        
        // 检查选择范围是否跨过了完整的字段或方法边界
        PsiElement startElement = findContainingElement(psiFile, start);
        PsiElement endElement = findContainingElement(psiFile, end - 1); // -1 to get element at end position
        
        // 有效的选择应该包含完整的字段或方法定义
        boolean startAtElementBoundary = startElement instanceof PsiField || startElement instanceof PsiMethod;
        boolean endAtElementBoundary = endElement instanceof PsiField || endElement instanceof PsiMethod;
        
        // 如果开始和结束位置都在元素边界上，这可能意味着选择是有效的
        // 我们还需要检查选择的文本是否确实是完整的元素定义
        if (startAtElementBoundary && endAtElementBoundary && startElement == endElement) {
            // 如果开始和结束都在同一元素内，我们需要判断是否选择了整个元素
            return isSelectionCompleteElement(startElement, start, end);
        }
        
        // 如果选择跨越多个元素，检查是否没有截断任何元素
        return !isSelectionCuttingElement(editor, psiFile, start, end);
    }
    
    /**
     * 检查选择是否截断了元素
     */
    private static boolean isSelectionCuttingElement(Editor editor, PsiFile psiFile, int start, int end) {
        // 遍历选中的范围，看是否包含完整的方法或字段定义
        PsiElement elementAtStart = psiFile.findElementAt(start);
        PsiElement elementAtEnd = psiFile.findElementAt(end - 1); // -1 to get element at end position
        
        if (elementAtStart != null && elementAtEnd != null) {
            // 检查起始位置是否在元素的开始处
            boolean startAtBeginning = elementAtStart.getTextOffset() == start;
            
            // 检查结束位置是否在元素的结束处
            boolean endAtEnd = (elementAtEnd.getTextOffset() + elementAtEnd.getTextLength()) == end;
            
            // 如果起始位置不是元素的开始，或者结束位置不是元素的结束，则选择截断了元素
            if (!startAtBeginning || !endAtEnd) {
                return true;
            }
        }
        
        // 检查选区内是否有完整的方法或字段
        String selectedText = editor.getDocument().getText(new TextRange(start, end));
        return !selectedText.trim().isEmpty() && !hasCompleteElementsOnly(psiFile, start, end);
    }
    
    /**
     * 检查选择范围是否只包含完整的元素
     */
    private static boolean hasCompleteElementsOnly(PsiFile psiFile, int start, int end) {
        // 简化实现，检查选择范围是否包含完整的方法和字段定义
        // 这里需要更复杂的逻辑来验证元素完整性
        return true; // 临时返回true，以允许功能工作
    }
    
    /**
     * 检查选择是否包含完整的单个元素
     */
    private static boolean isSelectionCompleteElement(PsiElement element, int start, int end) {
        int elementStart = element.getTextOffset();
        int elementEnd = elementStart + element.getTextLength();
        
        return start == elementStart && end == elementEnd;
    }
    
    /**
     * 找到给定偏移位置所在的元素
     */
    private static PsiElement findContainingElement(PsiFile psiFile, int offset) {
        if (offset >= psiFile.getTextLength()) {
            offset = psiFile.getTextLength() - 1;
        }
        if (offset < 0) {
            return null;
        }
        return psiFile.findElementAt(offset);
    }
}
