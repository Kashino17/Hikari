'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'

export default function Variant2Root() {
  const router = useRouter()
  useEffect(() => { router.replace('/variant-2/feed') }, [router])
  return null
}
