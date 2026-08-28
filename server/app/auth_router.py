from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app import models, schemas, security
from app.database import get_db

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


@router.post(
    "/register",
    response_model=schemas.UserOut,
    status_code=status.HTTP_201_CREATED,
)
def register(request: schemas.UserRegisterRequest, db: Session = Depends(get_db)):
    existing = db.query(models.User).filter(models.User.username == request.username).first()
    if existing is not None:
        raise HTTPException(status_code=409, detail="username already exists")

    user = models.User(
        username=request.username,
        nickname=request.username,
        password_hash=security.hash_password(request.password),
    )
    db.add(user)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="username already exists")
    db.refresh(user)
    return user


@router.post("/login", response_model=schemas.LoginResponse)
def login(request: schemas.LoginRequest, db: Session = Depends(get_db)):
    user = db.query(models.User).filter(models.User.username == request.username).first()
    if user is None or not security.verify_password(request.password, user.password_hash):
        raise HTTPException(status_code=401, detail="invalid credentials")

    token = security.create_access_token(user.id, user.username)
    return schemas.LoginResponse(
        token=token,
        user=schemas.UserOut(id=user.id, username=user.username, nickname=user.nickname),
    )
