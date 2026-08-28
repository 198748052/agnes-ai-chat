import base64
import os
from datetime import datetime, timezone, timedelta

import jwt
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app import models, schemas, security
from app.database import get_db

router = APIRouter(prefix="/api/v1/user", tags=["user"])

bearer_scheme = HTTPBearer(auto_error=False)

UPLOADS_ROOT = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "uploads")
)
AVATAR_DIR = os.path.join(UPLOADS_ROOT, "avatars")
MAX_AVATAR_BYTES = 5 * 1024 * 1024


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> models.User:
    if credentials is None:
        raise HTTPException(status_code=401, detail="unauthorized")
    try:
        payload = jwt.decode(credentials.credentials, security.JWT_SECRET, algorithms=[security.JWT_ALGORITHM])
        user_id = int(payload["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise HTTPException(status_code=401, detail="unauthorized")
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if user is None:
        raise HTTPException(status_code=401, detail="unauthorized")
    return user


@router.put("/profile", response_model=schemas.UserOut)
def update_profile(
    request: schemas.UpdateProfileRequest,
    user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    user.nickname = request.nickname
    db.commit()
    db.refresh(user)
    return user


@router.post("/password")
def change_password(
    request: schemas.ChangePasswordRequest,
    user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if not security.verify_password(request.old_password, user.password_hash):
        raise HTTPException(status_code=401, detail="old password incorrect")
    user.password_hash = security.hash_password(request.new_password)
    db.commit()
    return {"detail": "ok"}


@router.post("/avatar", response_model=schemas.AvatarResponse)
def upload_avatar(
    request: schemas.UploadAvatarRequest,
    user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    try:
        data = base64.b64decode(request.avatar_base64, validate=True)
    except Exception:
        raise HTTPException(status_code=400, detail="invalid image")
    if len(data) > MAX_AVATAR_BYTES:
        raise HTTPException(status_code=413, detail="image too large")
    os.makedirs(AVATAR_DIR, exist_ok=True)
    avatar_path = os.path.join(AVATAR_DIR, f"{user.id}.jpg")
    with open(avatar_path, "wb") as f:
        f.write(data)
    avatar_url = f"/uploads/avatars/{user.id}.jpg"
    user.avatar_url = avatar_url
    db.commit()
    return schemas.AvatarResponse(avatar_url=avatar_url)


def _period_counts(created_ats):
    """按 UTC 时区聚合今日 / 本周 / 本月 / 累计计数。"""
    now = datetime.now(timezone.utc)
    today_start = datetime(now.year, now.month, now.day, tzinfo=timezone.utc)
    week_base = now - timedelta(days=now.weekday())
    week_start = datetime(week_base.year, week_base.month, week_base.day, tzinfo=timezone.utc)
    month_start = datetime(now.year, now.month, 1, tzinfo=timezone.utc)
    today = week = month = 0
    for ts in created_ats:
        dt = datetime.fromtimestamp(ts / 1000, tz=timezone.utc)
        if dt >= today_start:
            today += 1
        if dt >= week_start:
            week += 1
        if dt >= month_start:
            month += 1
    return {"today": today, "week": week, "month": month, "total": len(created_ats)}


@router.get("/stats", response_model=schemas.UserStats)
def get_stats(
    user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    records = (
        db.query(models.GenerationUsage)
        .filter(models.GenerationUsage.user_id == user.id)
        .all()
    )
    image_ts = [
        int(r.created_at.replace(tzinfo=timezone.utc).timestamp() * 1000)
        for r in records
        if r.type == "image"
    ]
    video_ts = [
        int(r.created_at.replace(tzinfo=timezone.utc).timestamp() * 1000)
        for r in records
        if r.type == "video"
    ]
    return schemas.UserStats(
        image=schemas.PeriodCounts(**_period_counts(image_ts)),
        video=schemas.PeriodCounts(**_period_counts(video_ts)),
    )
