from fastapi import APIRouter

router = APIRouter(prefix="/api/v1", tags=["meta"])

ANNOUNCEMENTS = [
    {
        "id": "welcome",
        "title": "欢迎使用 Agnes AI Chat",
        "content": "Agnes AI Chat 是一款基于 Agnes 2.5 Flash 大模型的安卓聊天应用，支持实时流式对话、多轮上下文、聊天记录本地持久化，以及 AI 图片 / 视频创作。",
        "priority": "important",
        "publish_at": 0,
    },
    {
        "id": "api-key",
        "title": "使用前请先配置 API Key",
        "content": "首次使用请在「我的」-「设置」中填入你的 Agnes API Key，并根据需要自定义系统提示词。",
        "priority": "normal",
        "publish_at": 0,
    },
]

VERSION_INFO = {
    "latest_version_code": 1,
    "latest_version_name": "1.0",
    "force_update": False,
    "update_log": "",
    "download_url": "",
}


@router.get("/announcements/latest")
def get_latest_announcement():
    if not ANNOUNCEMENTS:
        return {"detail": "no announcement"}
    return ANNOUNCEMENTS[0]


@router.get("/app/version")
def get_app_version():
    return VERSION_INFO
