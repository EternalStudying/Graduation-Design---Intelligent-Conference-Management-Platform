import fs from "node:fs"
import path from "node:path"
import {
  AlignmentType,
  Document,
  Footer,
  HeadingLevel,
  Header,
  LevelFormat,
  LineRuleType,
  Packer,
  PageNumber,
  Paragraph,
  TableOfContents,
  TextRun
} from "docx"

const outputPath = path.resolve(
  process.cwd(),
  "..",
  "..",
  "会议室预约管理系统论文目录（定制版）.docx"
)

const title = "会议室预约管理系统设计与实现"

const chapterData = [
  {
    heading: "摘要",
    points: [
      "概括课题背景、研究目的、系统实现内容、采用的关键技术和最终达到的效果。",
      "摘要要直接点出本系统围绕会议室预约场景，完成了前后端分离的管理系统开发。",
      "中文摘要一般控制在 400 字以内，突出系统实现成果和创新点。",
      "关键词建议控制在 3 到 5 个，例如：会议室预约；Vue3；Spring Boot；MyBatis；智能助手。"
    ]
  },
  {
    heading: "ABSTRACT",
    points: [
      "与中文摘要内容保持一致，使用英文表达系统背景、功能模块、技术路线和实现成果。",
      "英文关键词建议与中文关键词一一对应，数量保持一致，使用分号分隔。"
    ]
  },
  {
    heading: "1 绪论",
    sections: [
      {
        heading: "1.1 课题背景与研究意义",
        points: [
          "说明企事业单位日常会议管理中存在人工登记效率低、会议室冲突频繁、信息统计困难等问题。",
          "说明开发会议室预约管理系统在提高资源利用率、规范预约流程和提升管理效率方面的实际价值。"
        ]
      },
      {
        heading: "1.2 国内外研究现状",
        points: [
          "简述现有办公管理系统、预约系统和智能助手类功能的发展情况。",
          "可从传统信息管理系统、Web 化预约平台、结合智能问答的管理系统三个角度概括。"
        ]
      },
      {
        heading: "1.3 研究内容",
        points: [
          "说明本文围绕会议室预约完整业务流程，完成前端页面、后端接口、数据库设计和系统测试。",
          "明确系统包含登录认证、会议室管理、设备管理、预约管理、日历展示、通知提醒、统计分析和 AI 助手等模块。"
        ]
      },
      {
        heading: "1.4 论文结构安排",
        points: [
          "简述各章节的主要内容，让目录结构和正文内容形成对应关系。"
        ]
      }
    ]
  },
  {
    heading: "2 相关技术与开发工具",
    sections: [
      {
        heading: "2.1 前端技术",
        points: [
          "Vue3：说明基于组件化思想构建页面，提高界面复用性与可维护性。",
          "Vite：说明其在前端工程中的快速启动和高效构建优势。",
          "TypeScript：说明静态类型约束对前端项目开发质量的提升。",
          "Element Plus：说明用于快速搭建后台管理界面组件。",
          "Pinia 与 Vue Router：分别说明状态管理和页面路由组织方式。",
          "ECharts 与 FullCalendar：分别用于统计图表展示和预约日历视图展示。"
        ]
      },
      {
        heading: "2.2 后端技术",
        points: [
          "Spring Boot：说明其在 Web 服务搭建、配置管理和模块整合方面的作用。",
          "MyBatis：说明其在数据库访问层中的映射与持久化能力。",
          "MySQL：说明其在业务数据存储中的应用。",
          "Spring Validation：说明其在接口参数校验中的作用。"
        ]
      },
      {
        heading: "2.3 智能辅助相关技术",
        points: [
          "Spring AI：说明其在大模型能力接入中的封装作用。",
          "Ollama 与本地模型：说明系统通过本地模型实现会议室预约问答与辅助操作。"
        ]
      },
      {
        heading: "2.4 开发工具与运行环境",
        points: [
          "说明开发环境可包括 IntelliJ IDEA、VS Code、Navicat、Apifox 或 Postman、Node.js、Maven 等。",
          "可给出前端、后端、数据库和本地模型服务的运行环境说明。"
        ]
      }
    ]
  },
  {
    heading: "3 系统需求分析",
    sections: [
      {
        heading: "3.1 系统目标",
        points: [
          "明确系统目标是实现会议室资源的统一管理、预约流程在线化和统计分析可视化。",
          "补充系统需要兼顾普通用户与管理员两类角色。"
        ]
      },
      {
        heading: "3.2 可行性分析",
        points: [
          "技术可行性：前后端分离架构成熟，相关框架稳定，可满足项目开发需求。",
          "经济可行性：所用开发工具与技术栈以开源方案为主，开发和部署成本较低。",
          "操作可行性：系统界面直观，适合普通用户与管理员使用。"
        ]
      },
      {
        heading: "3.3 角色与业务分析",
        points: [
          "普通用户：登录系统、查看会议室、按时间预约、查看个人预约、接收通知、使用 AI 助手。",
          "管理员：管理会议室信息、设备信息、统计数据与系统资源。"
        ]
      },
      {
        heading: "3.4 功能需求分析",
        points: [
          "用户认证需求：实现登录、身份识别与权限控制。",
          "会议室管理需求：展示会议室信息、容量、状态和设备情况。",
          "预约管理需求：支持创建预约、查询预约、取消预约和日历查看。",
          "设备管理需求：支持设备维护、绑定统计与设备状态展示。",
          "通知与统计需求：支持消息提醒和数据可视化分析。",
          "AI 助手需求：支持自然语言查询会议室信息、预约信息和操作指引。"
        ]
      },
      {
        heading: "3.5 非功能需求分析",
        points: [
          "性能需求：常规页面与接口响应时间应满足日常使用要求。",
          "安全需求：需要具备身份认证、接口权限控制和基础异常处理能力。",
          "可维护性需求：模块划分清晰，便于后续扩展和升级。"
        ]
      }
    ]
  },
  {
    heading: "4 系统总体设计",
    sections: [
      {
        heading: "4.1 系统架构设计",
        points: [
          "说明系统采用前后端分离架构，前端负责页面展示与交互，后端负责业务处理与数据访问，数据库负责数据持久化。",
          "可以配合系统总体架构图说明前端、后端、数据库和 AI 服务之间的关系。"
        ]
      },
      {
        heading: "4.2 系统功能模块设计",
        points: [
          "从功能角度划分为登录认证模块、会议室管理模块、设备管理模块、预约管理模块、通知模块、统计分析模块和 AI 助手模块。",
          "每个模块建议配一个简要说明或模块结构图。"
        ]
      },
      {
        heading: "4.3 数据库设计",
        points: [
          "先说明数据库设计原则，再介绍核心实体之间的关系。",
          "重点可写用户表、会议室表、设备表、预约表、通知表等核心数据表。",
          "建议给出 E-R 图和主要数据表结构说明。"
        ]
      },
      {
        heading: "4.4 接口设计",
        points: [
          "说明前后端主要接口的组织方式，例如认证接口、会议室接口、预约接口、统计接口和 AI 助手接口。",
          "可列出典型接口的请求路径、请求参数和返回结果。"
        ]
      }
    ]
  },
  {
    heading: "5 系统详细实现",
    sections: [
      {
        heading: "5.1 登录认证功能实现",
        points: [
          "说明前端登录页、后端登录接口、身份校验与权限控制的实现方式。",
          "可结合拦截器或认证上下文说明请求鉴权过程。"
        ]
      },
      {
        heading: "5.2 会议室管理功能实现",
        points: [
          "说明会议室列表展示、会议室信息维护、状态展示和设备关联等功能实现。",
          "前端可结合管理页面，后端可结合控制器、服务层和数据访问层描述。"
        ]
      },
      {
        heading: "5.3 设备管理功能实现",
        points: [
          "说明设备的新增、修改、绑定和统计展示实现。",
          "如果系统包含设备绑定统计页面，可重点说明统计逻辑与图表展示。"
        ]
      },
      {
        heading: "5.4 预约管理与日历功能实现",
        points: [
          "说明预约创建弹窗、预约时间校验、预约记录查询与取消操作。",
          "说明 FullCalendar 页面如何展示预约数据，提高用户对会议安排的可视化理解。"
        ]
      },
      {
        heading: "5.5 通知与统计分析功能实现",
        points: [
          "说明通知列表、已读处理、概览页面和统计图表的实现方式。",
          "统计分析可围绕会议室使用情况、设备绑定情况和预约数据展开。"
        ]
      },
      {
        heading: "5.6 AI 助手功能实现",
        points: [
          "说明系统如何接入 Spring AI 与 Ollama，实现基于自然语言的查询与辅助交互。",
          "可写清楚意图解析、会话存储、业务动作分发和结果返回的大致流程。",
          "这一节是本系统区别于传统预约管理系统的亮点内容。"
        ]
      }
    ]
  },
  {
    heading: "6 系统测试",
    sections: [
      {
        heading: "6.1 测试环境",
        points: [
          "说明前端、后端、数据库和本地模型服务的测试环境与版本信息。"
        ]
      },
      {
        heading: "6.2 功能测试",
        points: [
          "围绕登录、会议室管理、设备管理、预约管理、通知、统计和 AI 助手等模块设计测试用例。",
          "建议用表格展示测试编号、测试内容、预期结果和实际结果。"
        ]
      },
      {
        heading: "6.3 测试结果分析",
        points: [
          "总结系统主要功能是否达到预期，指出目前仍存在的不足，例如智能助手能力边界、并发场景测试不足等。"
        ]
      }
    ]
  },
  {
    heading: "7 总结与展望",
    sections: [
      {
        heading: "7.1 工作总结",
        points: [
          "总结本文完成了会议室预约管理系统的需求分析、架构设计、功能实现与测试验证。",
          "强调系统已基本实现会议室预约流程在线化、数据管理规范化和统计展示可视化。"
        ]
      },
      {
        heading: "7.2 未来展望",
        points: [
          "可展望移动端适配、审批流程扩展、消息推送优化、权限模型细化和 AI 助手能力增强等方向。"
        ]
      }
    ]
  },
  {
    heading: "致谢",
    points: [
      "感谢指导教师、项目参与者与学校提供的学习和实践环境，文字简洁真实即可。"
    ]
  },
  {
    heading: "参考文献",
    points: [
      "优先引用教材、论文、官方文档和与你课题直接相关的研究资料。",
      "参考文献建议不少于 10 篇，其中外文文献不少于 2 篇，期刊论文不少于 4 篇。",
      "中英文参考文献格式要符合学校要求，正文引用与文后条目保持一致。"
    ]
  },
  {
    heading: "附录（必要时）",
    points: [
      "如程序清单、补充数据表、关键接口列表、问卷或其他不适合放入正文但有保留价值的内容，可放入附录。"
    ]
  }
]

function heading(text, level, pageBreakBefore = false) {
  return new Paragraph({
    pageBreakBefore,
    heading: level,
    children: [new TextRun(text)]
  })
}

function bullet(text, reference = "bullets") {
  return new Paragraph({
    numbering: {
      reference,
      level: 0
    },
    spacing: {
      after: 120,
      line: 400,
      lineRule: LineRuleType.EXACT
    },
    children: [new TextRun(text)]
  })
}

function buildChildren() {
  const children = [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: {
        before: 240,
        after: 360
      },
      children: [
        new TextRun({
          text: title,
          bold: true,
          size: 36,
          font: "黑体"
        })
      ]
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: {
        after: 480
      },
      children: [
        new TextRun({
          text: "论文目录与各章写作要点",
          size: 24,
          font: "宋体"
        })
      ]
    }),
    new Paragraph({
      spacing: {
        after: 180
      },
      children: [
        new TextRun({
          text: "说明：本文件依据现有前后端代码结构整理，并结合参考规范调整标题层级、目录层级与页面设置，可直接作为论文写作提纲使用。",
          italics: true
        })
      ]
    }),
    new TableOfContents("目录", {
      hyperlink: true,
      headingStyleRange: "1-2"
    }),
    new Paragraph({ pageBreakBefore: true })
  ]

  let firstChapter = true
  for (const chapter of chapterData) {
    children.push(heading(chapter.heading, HeadingLevel.HEADING_1, !firstChapter))
    firstChapter = false

    if (chapter.points) {
      for (const point of chapter.points) {
        children.push(bullet(point))
      }
    }

    if (chapter.sections) {
      for (const section of chapter.sections) {
        children.push(heading(section.heading, HeadingLevel.HEADING_2))
        for (const point of section.points) {
          children.push(bullet(point, "subBullets"))
        }
      }
    }
  }

  return children
}

const doc = new Document({
  styles: {
    default: {
      document: {
        run: {
          font: "宋体",
          size: 24
        }
      }
    },
    paragraphStyles: [
      {
        id: "Heading1",
        name: "Heading 1",
        basedOn: "Normal",
        next: "Normal",
        quickFormat: true,
        run: {
          size: 32,
          bold: true,
          font: "黑体"
        },
        paragraph: {
          alignment: AlignmentType.CENTER,
          spacing: {
            before: 280,
            after: 180,
            line: 240,
            lineRule: LineRuleType.EXACT
          },
          outlineLevel: 0
        }
      },
      {
        id: "Heading2",
        name: "Heading 2",
        basedOn: "Normal",
        next: "Normal",
        quickFormat: true,
        run: {
          size: 28,
          bold: true,
          font: "黑体"
        },
        paragraph: {
          spacing: {
            before: 200,
            after: 120,
            line: 240,
            lineRule: LineRuleType.EXACT
          },
          outlineLevel: 1
        }
      }
    ]
  },
  numbering: {
    config: [
      {
        reference: "bullets",
        levels: [
          {
            level: 0,
            format: LevelFormat.BULLET,
            text: "•",
            alignment: AlignmentType.LEFT,
            style: {
              paragraph: {
                indent: {
                  left: 720,
                  hanging: 360
                }
              }
            }
          }
        ]
      },
      {
        reference: "subBullets",
        levels: [
          {
            level: 0,
            format: LevelFormat.BULLET,
            text: "•",
            alignment: AlignmentType.LEFT,
            style: {
              paragraph: {
                indent: {
                  left: 720,
                  hanging: 360
                }
              }
            }
          }
        ]
      }
    ]
  },
  sections: [
    {
      properties: {
        page: {
          size: {
            width: 11906,
            height: 16838
          },
          margin: {
            top: 1701,
            right: 1134,
            bottom: 1134,
            left: 1701,
            header: 1134,
            footer: 567,
            gutter: 567
          }
        }
      },
      headers: {
        default: new Header({
          children: [
            new Paragraph({
              alignment: AlignmentType.CENTER,
              children: [
                new TextRun({
                  text: "毕业论文写作提纲",
                  size: 21
                })
              ]
            })
          ]
        })
      },
      footers: {
        default: new Footer({
          children: [
            new Paragraph({
              alignment: AlignmentType.CENTER,
              children: [
                new TextRun("第 "),
                new TextRun({ children: [PageNumber.CURRENT] }),
                new TextRun(" 页")
              ]
            })
          ]
        })
      },
      children: buildChildren()
    }
  ]
})

const buffer = await Packer.toBuffer(doc)
fs.writeFileSync(outputPath, buffer)
console.log(outputPath)
