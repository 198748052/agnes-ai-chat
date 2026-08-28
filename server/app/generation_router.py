from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app import models, schemas
from app.database import get_db
from app.user_router import get_current_user

router = APIRouter(prefix="/api/v1/generation", tags=["generation"])


def _record_and_mock(user: models.User, db: Session, gen_type: str) -> schemas.GenerationResponse:
    db.add(models.GenerationUsage(user_id=user.id, type=gen_type))
    db.commit()
    return schemas.GenerationResponse(task_id="mock-task")


@router.post("/image", response_model=schemas.GenerationResponse)
def generate_image(
    request: schemas.GenerationRequest,
    user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return _record_and_mock(user, db, "image")


@router.post("/video", response_model=schemas.GenerationResponse)
def generate_video(
    request: schemas.GenerationRequest,
    user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return _record_and_mock(user, db, "video")
