import os

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from app import models  # noqa: F401 确保建表前注册模型
from app.auth_router import router as auth_router
from app.database import Base, engine
from app.generation_router import router as generation_router
from app.meta_router import router as meta_router
from app.user_router import router as user_router

Base.metadata.create_all(bind=engine)

app = FastAPI(title="Agnes AI Chat Auth Server", version="1.0.0")

UPLOADS_ROOT = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "uploads")
)
os.makedirs(UPLOADS_ROOT, exist_ok=True)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={"detail": "invalid request"},
    )


app.include_router(auth_router)
app.include_router(meta_router)
app.include_router(user_router)
app.include_router(generation_router)
app.mount("/uploads", StaticFiles(directory=UPLOADS_ROOT), name="uploads")


@app.get("/health")
def health():
    return {"status": "ok"}
