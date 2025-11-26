# Frontend Integration Guide

프론트엔드와 Backend API 연동 시 필수 고려사항 가이드

---

## 📋 목차
1. [S3 파일 업로드](#s3-파일-업로드)
2. [Content-Type 설정](#content-type-설정)
3. [파일 크기 제한](#파일-크기-제한)
4. [파일 확장자 검증](#파일-확장자-검증)
5. [에러 처리](#에러-처리)
6. [업로드 진행률](#업로드-진행률)
7. [S3 CORS 설정](#s3-cors-설정)
8. [보안 고려사항](#보안-고려사항)

---

## 1. S3 파일 업로드

### ⚠️ 중요: Content-Type 설정 필수!

S3에 직접 업로드할 때 **반드시 Content-Type을 설정**해야 합니다.
설정하지 않으면 파일 다운로드 시 브라우저가 올바르게 처리하지 못합니다.

### 올바른 업로드 방법

```javascript
// ❌ 잘못된 방법 - Content-Type 없음
await fetch(uploadUrl, {
  method: 'PUT',
  body: file
});

// ✅ 올바른 방법 - Content-Type 포함
await fetch(uploadUrl, {
  method: 'PUT',
  headers: {
    'Content-Type': file.type  // 중요!
  },
  body: file
});
```

### 타입별 완전한 예시

#### 스토리(소설) 파일 업로드
```javascript
async function uploadStoryFile(file) {
  try {
    // 1. Pre-signed URL 요청
    const urlResponse = await fetch(
      `/api/upload/story/presigned-url?fileName=${encodeURIComponent(file.name)}`
    );

    if (!urlResponse.ok) {
      throw new Error('Failed to get upload URL');
    }

    const { uploadUrl, fileKey } = await urlResponse.json();

    // 2. S3에 직접 업로드 (Content-Type 필수!)
    const uploadResponse = await fetch(uploadUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': file.type || 'text/plain'
      },
      body: file
    });

    if (!uploadResponse.ok) {
      throw new Error('Failed to upload file to S3');
    }

    // 3. Backend에 스토리 정보 등록
    const storyResponse = await fetch('/api/stories/upload-from-s3', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        title: '나의 소설',
        fileKey: fileKey
      })
    });

    const storyData = await storyResponse.json();
    return storyData;

  } catch (error) {
    console.error('Upload failed:', error);
    throw error;
  }
}
```

#### 이미지 파일 업로드
```javascript
async function uploadImage(imageFile) {
  // 1. Pre-signed URL 요청
  const urlResponse = await fetch(
    `/api/upload/image/presigned-url?fileName=${encodeURIComponent(imageFile.name)}`
  );
  const { uploadUrl, fileKey } = await urlResponse.json();

  // 2. S3 업로드 (이미지 Content-Type)
  await fetch(uploadUrl, {
    method: 'PUT',
    headers: {
      'Content-Type': imageFile.type  // image/jpeg, image/png 등
    },
    body: imageFile
  });

  return fileKey;
}
```

#### 동영상 파일 업로드
```javascript
async function uploadVideo(videoFile) {
  // 1. Pre-signed URL 요청
  const urlResponse = await fetch(
    `/api/upload/video/presigned-url?fileName=${encodeURIComponent(videoFile.name)}`
  );
  const { uploadUrl, fileKey } = await urlResponse.json();

  // 2. S3 업로드 (동영상 Content-Type)
  await fetch(uploadUrl, {
    method: 'PUT',
    headers: {
      'Content-Type': videoFile.type  // video/mp4 등
    },
    body: videoFile
  });

  return fileKey;
}
```

---

## 2. Content-Type 설정

### 파일 타입별 올바른 Content-Type

| 파일 타입 | 확장자 | Content-Type |
|----------|--------|--------------|
| 텍스트 | .txt | `text/plain` |
| PDF | .pdf | `application/pdf` |
| Word | .docx | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| JPEG | .jpg, .jpeg | `image/jpeg` |
| PNG | .png | `image/png` |
| GIF | .gif | `image/gif` |
| MP4 | .mp4 | `video/mp4` |
| AVI | .avi | `video/x-msvideo` |
| MOV | .mov | `video/quicktime` |

### Content-Type 자동 감지

```javascript
function getContentType(file) {
  // 브라우저가 감지한 타입 우선 사용
  if (file.type) {
    return file.type;
  }

  // fallback: 확장자로 판단
  const extension = file.name.split('.').pop().toLowerCase();
  const contentTypes = {
    'txt': 'text/plain',
    'pdf': 'application/pdf',
    'jpg': 'image/jpeg',
    'jpeg': 'image/jpeg',
    'png': 'image/png',
    'gif': 'image/gif',
    'mp4': 'video/mp4',
    'avi': 'video/x-msvideo',
    'mov': 'video/quicktime'
  };

  return contentTypes[extension] || 'application/octet-stream';
}

// 사용
await fetch(uploadUrl, {
  method: 'PUT',
  headers: {
    'Content-Type': getContentType(file)
  },
  body: file
});
```

---

## 3. 파일 크기 제한

### Frontend 검증

```javascript
const MAX_FILE_SIZES = {
  story: 10 * 1024 * 1024,   // 10MB (소설 텍스트)
  image: 5 * 1024 * 1024,    // 5MB (이미지)
  video: 100 * 1024 * 1024   // 100MB (동영상)
};

function validateFileSize(file, type) {
  const maxSize = MAX_FILE_SIZES[type];
  if (file.size > maxSize) {
    throw new Error(`파일 크기는 ${maxSize / 1024 / 1024}MB를 초과할 수 없습니다.`);
  }
}

// 사용
try {
  validateFileSize(file, 'story');
  await uploadStoryFile(file);
} catch (error) {
  alert(error.message);
}
```

### 파일 크기 표시

```javascript
function formatFileSize(bytes) {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

console.log(formatFileSize(file.size)); // "2.5 MB"
```

---

## 4. 파일 확장자 검증

### 허용된 확장자 검증

```javascript
const ALLOWED_EXTENSIONS = {
  story: ['txt', 'pdf', 'doc', 'docx'],
  image: ['jpg', 'jpeg', 'png', 'gif', 'webp'],
  video: ['mp4', 'avi', 'mov', 'wmv', 'flv']
};

function validateFileExtension(file, type) {
  const extension = file.name.split('.').pop().toLowerCase();
  const allowed = ALLOWED_EXTENSIONS[type];

  if (!allowed.includes(extension)) {
    throw new Error(
      `허용되지 않는 파일 형식입니다. 허용: ${allowed.join(', ')}`
    );
  }
}

// 사용
try {
  validateFileExtension(file, 'story');
  validateFileSize(file, 'story');
  await uploadStoryFile(file);
} catch (error) {
  alert(error.message);
}
```

---

## 5. 에러 처리

### 포괄적인 에러 처리

```javascript
async function uploadFileWithErrorHandling(file, type) {
  try {
    // 1. 파일 검증
    validateFileExtension(file, type);
    validateFileSize(file, type);

    // 2. Pre-signed URL 요청
    const urlResponse = await fetch(
      `/api/upload/${type}/presigned-url?fileName=${encodeURIComponent(file.name)}`
    );

    if (!urlResponse.ok) {
      const error = await urlResponse.json();
      throw new Error(error.message || 'URL 생성 실패');
    }

    const { uploadUrl, fileKey } = await urlResponse.json();

    // 3. S3 업로드
    const uploadResponse = await fetch(uploadUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': getContentType(file)
      },
      body: file
    });

    if (!uploadResponse.ok) {
      // S3 에러 처리
      const errorText = await uploadResponse.text();
      console.error('S3 upload error:', errorText);
      throw new Error('파일 업로드에 실패했습니다.');
    }

    return { fileKey, success: true };

  } catch (error) {
    // 에러 타입별 처리
    if (error.name === 'TypeError' && error.message.includes('fetch')) {
      // 네트워크 에러
      return {
        success: false,
        error: '네트워크 연결을 확인해주세요.'
      };
    } else if (error.message.includes('파일')) {
      // 파일 검증 에러
      return {
        success: false,
        error: error.message
      };
    } else {
      // 기타 에러
      return {
        success: false,
        error: '파일 업로드 중 오류가 발생했습니다.'
      };
    }
  }
}
```

### React 컴포넌트 예시

```jsx
function FileUploader() {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);
  const [progress, setProgress] = useState(0);

  const handleUpload = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    setUploading(true);
    setError(null);
    setProgress(0);

    try {
      const result = await uploadFileWithErrorHandling(file, 'story');

      if (result.success) {
        alert('업로드 성공!');
        // 다음 단계로...
      } else {
        setError(result.error);
      }
    } catch (err) {
      setError('업로드 중 오류가 발생했습니다.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <input
        type="file"
        onChange={handleUpload}
        disabled={uploading}
      />
      {uploading && <p>업로드 중... {progress}%</p>}
      {error && <p style={{color: 'red'}}>{error}</p>}
    </div>
  );
}
```

---

## 6. 업로드 진행률

### XMLHttpRequest를 사용한 진행률 표시

```javascript
function uploadWithProgress(file, uploadUrl, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    // 진행률 이벤트
    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        const percentComplete = (event.loaded / event.total) * 100;
        onProgress(Math.round(percentComplete));
      }
    });

    // 완료 이벤트
    xhr.addEventListener('load', () => {
      if (xhr.status === 200) {
        resolve();
      } else {
        reject(new Error(`Upload failed: ${xhr.status}`));
      }
    });

    // 에러 이벤트
    xhr.addEventListener('error', () => {
      reject(new Error('Network error'));
    });

    // 요청 전송
    xhr.open('PUT', uploadUrl);
    xhr.setRequestHeader('Content-Type', getContentType(file));
    xhr.send(file);
  });
}

// 사용
await uploadWithProgress(file, uploadUrl, (progress) => {
  console.log(`Upload progress: ${progress}%`);
  setProgress(progress);
});
```

### React 진행률 바 컴포넌트

```jsx
function ProgressBar({ progress }) {
  return (
    <div style={{ width: '100%', backgroundColor: '#e0e0e0', borderRadius: '4px' }}>
      <div
        style={{
          width: `${progress}%`,
          height: '20px',
          backgroundColor: '#4caf50',
          borderRadius: '4px',
          transition: 'width 0.3s ease'
        }}
      >
        <span style={{ color: 'white', padding: '0 10px' }}>
          {progress}%
        </span>
      </div>
    </div>
  );
}
```

---

## 7. S3 CORS 설정

### ⚠️ 중요: S3 버킷에 CORS 설정 필수

S3에 직접 업로드하려면 **S3 버킷에 CORS 설정**이 되어있어야 합니다.

AWS S3 콘솔에서 버킷 → Permissions → CORS 설정:

```json
[
  {
    "AllowedHeaders": [
      "*"
    ],
    "AllowedMethods": [
      "GET",
      "PUT",
      "POST",
      "DELETE",
      "HEAD"
    ],
    "AllowedOrigins": [
      "http://localhost:3000",
      "http://localhost:5173",
      "https://yourdomain.com"
    ],
    "ExposeHeaders": [
      "ETag",
      "x-amz-server-side-encryption",
      "x-amz-request-id",
      "x-amz-id-2"
    ],
    "MaxAgeSeconds": 3600
  }
]
```

### 프로덕션 환경 CORS

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "PUT"],
    "AllowedOrigins": ["https://yourdomain.com"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

---

## 8. 보안 고려사항

### 1. Pre-signed URL 만료 시간
- Backend에서 15분으로 설정되어 있음
- Frontend에서 타임아웃 처리 필요

```javascript
const UPLOAD_TIMEOUT = 14 * 60 * 1000; // 14분 (여유있게)

async function uploadWithTimeout(file, uploadUrl) {
  const timeoutPromise = new Promise((_, reject) => {
    setTimeout(() => reject(new Error('Upload timeout')), UPLOAD_TIMEOUT);
  });

  const uploadPromise = fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': getContentType(file) },
    body: file
  });

  return Promise.race([uploadPromise, timeoutPromise]);
}
```

### 2. 파일명 인코딩
```javascript
// ✅ 올바른 방법 - 파일명 인코딩
const encodedFileName = encodeURIComponent(file.name);
const url = `/api/upload/story/presigned-url?fileName=${encodedFileName}`;

// ❌ 잘못된 방법 - 특수문자 문제 발생
const url = `/api/upload/story/presigned-url?fileName=${file.name}`;
```

### 3. 민감한 정보 로깅 방지
```javascript
// ❌ Pre-signed URL 로깅 금지 (보안 위험!)
console.log('Upload URL:', uploadUrl);

// ✅ 필요시 fileKey만 로깅
console.log('Upload started for:', fileKey);
```

### 4. HTTPS 사용
```javascript
// 프로덕션에서는 반드시 HTTPS 사용
const isProduction = process.env.NODE_ENV === 'production';
const apiUrl = isProduction
  ? 'https://api.yourdomain.com'
  : 'http://localhost:8080';
```

---

## 9. 완전한 통합 예시

### React + TypeScript 예시

```typescript
import { useState } from 'react';

interface UploadResult {
  success: boolean;
  fileKey?: string;
  error?: string;
}

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
const ALLOWED_EXTENSIONS = ['txt', 'pdf'];

export function StoryUploader() {
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const validateFile = (file: File): string | null => {
    // 크기 검증
    if (file.size > MAX_FILE_SIZE) {
      return '파일 크기는 10MB를 초과할 수 없습니다.';
    }

    // 확장자 검증
    const extension = file.name.split('.').pop()?.toLowerCase();
    if (!extension || !ALLOWED_EXTENSIONS.includes(extension)) {
      return `허용된 파일 형식: ${ALLOWED_EXTENSIONS.join(', ')}`;
    }

    return null;
  };

  const uploadFile = async (file: File): Promise<UploadResult> => {
    try {
      // 1. Pre-signed URL 요청
      const urlResponse = await fetch(
        `/api/upload/story/presigned-url?fileName=${encodeURIComponent(file.name)}`
      );

      if (!urlResponse.ok) {
        throw new Error('Failed to get upload URL');
      }

      const { uploadUrl, fileKey } = await urlResponse.json();

      // 2. S3 업로드 (XMLHttpRequest로 진행률 추적)
      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest();

        xhr.upload.addEventListener('progress', (event) => {
          if (event.lengthComputable) {
            const percent = Math.round((event.loaded / event.total) * 100);
            setProgress(percent);
          }
        });

        xhr.addEventListener('load', () => {
          if (xhr.status === 200) {
            resolve();
          } else {
            reject(new Error(`Upload failed: ${xhr.status}`));
          }
        });

        xhr.addEventListener('error', () => {
          reject(new Error('Network error'));
        });

        xhr.open('PUT', uploadUrl);
        xhr.setRequestHeader('Content-Type', file.type || 'text/plain');
        xhr.send(file);
      });

      return { success: true, fileKey };

    } catch (err) {
      return {
        success: false,
        error: err instanceof Error ? err.message : '업로드 실패'
      };
    }
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0];
    if (!selectedFile) return;

    const validationError = validateFile(selectedFile);
    if (validationError) {
      setError(validationError);
      return;
    }

    setFile(selectedFile);
    setError(null);
  };

  const handleUpload = async () => {
    if (!file) return;

    setUploading(true);
    setProgress(0);
    setError(null);

    const result = await uploadFile(file);

    if (result.success) {
      // 다음 단계: 스토리 생성 API 호출
      console.log('File uploaded:', result.fileKey);
      alert('업로드 성공!');
    } else {
      setError(result.error || '업로드 실패');
    }

    setUploading(false);
  };

  return (
    <div>
      <input
        type="file"
        accept=".txt,.pdf"
        onChange={handleFileChange}
        disabled={uploading}
      />

      {file && (
        <div>
          <p>선택된 파일: {file.name}</p>
          <p>크기: {(file.size / 1024 / 1024).toFixed(2)} MB</p>
          <button onClick={handleUpload} disabled={uploading}>
            업로드
          </button>
        </div>
      )}

      {uploading && (
        <div>
          <div style={{
            width: '100%',
            backgroundColor: '#e0e0e0',
            borderRadius: '4px',
            marginTop: '10px'
          }}>
            <div style={{
              width: `${progress}%`,
              height: '30px',
              backgroundColor: '#4caf50',
              borderRadius: '4px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'white',
              fontWeight: 'bold'
            }}>
              {progress}%
            </div>
          </div>
        </div>
      )}

      {error && (
        <p style={{ color: 'red' }}>{error}</p>
      )}
    </div>
  );
}
```

---

## 10. 체크리스트

프론트엔드 개발 시 확인 사항:

- [ ] Content-Type 헤더 설정
- [ ] 파일 크기 검증 (Frontend + Backend)
- [ ] 파일 확장자 검증
- [ ] 파일명 인코딩 (encodeURIComponent)
- [ ] 에러 처리 (네트워크, S3, 검증 에러)
- [ ] 업로드 진행률 표시
- [ ] 타임아웃 처리 (15분)
- [ ] S3 CORS 설정 확인
- [ ] HTTPS 사용 (프로덕션)
- [ ] Pre-signed URL 로깅 방지

---

## 11. 문제 해결

### Q: S3 업로드 시 CORS 에러 발생
**A**: S3 버킷의 CORS 설정을 확인하세요. AllowedOrigins에 프론트엔드 도메인이 포함되어야 합니다.

### Q: 업로드 후 파일 다운로드 시 브라우저에서 열리지 않음
**A**: Content-Type을 올바르게 설정했는지 확인하세요. 설정하지 않으면 `application/octet-stream`으로 저장됩니다.

### Q: 업로드는 성공했는데 Backend에서 파일을 찾을 수 없음
**A**: fileKey가 정확하게 전달되었는지 확인하세요. encodeURIComponent로 인코딩해야 합니다.

### Q: 큰 파일 업로드 시 타임아웃 발생
**A**: Pre-signed URL 유효시간(15분) 내에 업로드가 완료되어야 합니다. 파일 크기 제한을 설정하세요.

---

## 📞 추가 도움이 필요하시면

Backend API 문서:
- `STORY_GENERATION_API.md` - 스토리 생성 프로세스
- `AI_SERVER_S3_INTEGRATION.md` - AI 서버 연동

Swagger UI: `http://localhost:8080/swagger-ui/index.html`