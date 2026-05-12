from pathlib import Path

import win32com.client


DOCX_PATH = Path(r"C:\Users\Acer\Desktop\毕业论文第三版.docx")
PARA_FILE = Path(r"E:\project\javaProject\meeting_room\tmp\docs\doc_paragraphs_after.txt")


REPLACEMENTS = [
    ("然而", "不过"),
    ("基于这一问题", "根据这一问题"),
    ("一个基于 Vue3 与 Spring Boot 的前后端分离系统", "一个在 Vue3 与 Spring Boot 的基础上构建的前后端分离系统"),
    ("均基于 Vue3 实现", "均在 Vue3 的基础上实现"),
    ("均基于 Spring Boot 实现", "均在 Spring Boot 的基础上实现"),
    ("系统采用基于 token 的登录状态维护方案", "系统采用 token 登录状态维护方案"),
    ("项目基于 Vue 3.5.27 与 Vite 7.3.1", "项目在 Vue 3.5.27 与 Vite 7.3.1 的基础上"),
    ("项目基于 Spring Boot 3.5.4 开发", "项目在 Spring Boot 3.5.4 的基础上开发"),
]

PARAGRAPH_REPLACEMENTS = {
    144: "从整体业务关系来看，管理员负责维护会议室和设备等基础资源，普通用户依托这些资源完成查询和预约，系统再对预约结果进行记录、展示和统计分析，从而形成完整的会议室预约管理业务闭环。",
    191: "系统后端在 Spring Boot 的基础上开发，整体按照控制层、业务层和数据访问层进行组织。控制层负责接收前端请求并返回统一结果，业务层处理会议室预约、设备管理、通知处理、统计分析和 AI 辅助等核心逻辑，数据访问层负责与数据库交互。起初我们也尝试让 AI 动作直接调用底层业务服务，但发现误触发风险较高，最终改为“识别意图—补全参数—确认执行”的受控流程，并通过拦截器、参数校验和全局异常处理机制规范接口访问。这样更稳。值得注意的是，AI 接入最难的并不是生成回复，而是把模型输出约束在可审计、可回滚的业务边界内。",
    282: "系统提供会议室推荐功能。前端根据时间、人数和设备需求调用推荐接口，候选会议室再由后端结合容量、时间冲突和设备匹配结果返回；用户提交预约后，各项校验会被统一执行，之后预约主表及其关联数据被写入数据库。推荐逻辑被收拢到服务端后，容量、设备和时间约束可以在同一处被校验。这样更稳。笔者认为，这种设计虽然增加了接口依赖，但能够明显降低规则分散带来的维护风险。",
}


def load_paragraphs(path: Path) -> dict[int, str]:
    data: dict[int, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        idx, text = line.split("\t", 1)
        data[int(idx)] = text
    return data


def normalize(text: str) -> str:
    return text.replace("\r", "").replace("\x07", "").strip()


def main() -> None:
    word = win32com.client.DispatchEx("Word.Application")
    word.Visible = False
    word.DisplayAlerts = 0

    try:
        doc = word.Documents.Open(str(DOCX_PATH), False, False)
        try:
            for old, new in REPLACEMENTS:
                find = doc.Content.Find
                find.ClearFormatting()
                find.Replacement.ClearFormatting()
                find.Execute(
                    FindText=old,
                    MatchCase=False,
                    MatchWholeWord=False,
                    MatchWildcards=False,
                    MatchSoundsLike=False,
                    MatchAllWordForms=False,
                    Forward=True,
                    Wrap=1,
                    Format=False,
                    ReplaceWith=new,
                    Replace=2,
                )

            current_paragraphs = load_paragraphs(PARA_FILE)
            old_to_new = {
                current_paragraphs[idx]: text for idx, text in PARAGRAPH_REPLACEMENTS.items()
            }
            replaced = set()
            for i in range(1, doc.Paragraphs.Count + 1):
                para = doc.Paragraphs(i)
                text = normalize(para.Range.Text)
                if text in old_to_new and text not in replaced:
                    para.Range.Text = old_to_new[text] + "\r"
                    replaced.add(text)

            for toc in doc.TablesOfContents:
                toc.Update()
            doc.Fields.Update()
            doc.Save()
        finally:
            doc.Close()
    finally:
        word.Quit()


if __name__ == "__main__":
    main()
