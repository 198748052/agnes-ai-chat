from pydantic import BaseModel, Field


class UserRegisterRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=64, description="登录账号")
    password: str = Field(..., min_length=6, max_length=128, description="密码，至少 6 位")


class LoginRequest(BaseModel):
    username: str = Field(..., min_length=1, description="登录账号")
    password: str = Field(..., min_length=1, description="登录密码")


class UserOut(BaseModel):
    id: int
    username: str
    nickname: str
    avatar_url: str | None = None

    model_config = {"from_attributes": True}


class LoginResponse(BaseModel):
    token: str
    user: UserOut


class UpdateProfileRequest(BaseModel):
    nickname: str = Field(..., min_length=1, max_length=20, description="新昵称，1-20 字")


class ChangePasswordRequest(BaseModel):
    old_password: str = Field(..., min_length=1, description="旧密码")
    new_password: str = Field(..., min_length=6, max_length=128, description="新密码，至少 6 位")


class UploadAvatarRequest(BaseModel):
    avatar_base64: str = Field(..., min_length=1, description="头像图片的 base64 编码内容")


class AvatarResponse(BaseModel):
    avatar_url: str


class PeriodCounts(BaseModel):
    today: int = 0
    week: int = 0
    month: int = 0
    total: int = 0


class UserStats(BaseModel):
    image: PeriodCounts = PeriodCounts()
    video: PeriodCounts = PeriodCounts()


class GenerationRequest(BaseModel):
    prompt: str = Field(..., min_length=1)
    mode: str = "image"


class GenerationResponse(BaseModel):
    task_id: str
    media_url: str | None = None
